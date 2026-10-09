package com.commontongue.speech.android

import android.content.Context
import com.commontongue.translation.AudioReference
import com.commontongue.translation.CapabilityDescription
import com.commontongue.translation.CapabilityFailure
import com.commontongue.translation.CapabilityFeature
import com.commontongue.translation.CapabilityResult
import com.commontongue.translation.ExecutionMode
import com.commontongue.translation.SpeakerPreservation
import com.commontongue.translation.SpeechPlayback
import com.commontongue.translation.SpeechPlaybackReceipt
import com.commontongue.translation.SpeechSynthesizer
import com.commontongue.translation.SynthesisRequest
import com.commontongue.translation.SynthesizedSpeech
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Main-dispatcher confined, latest request wins. Own this session at the application/UI boundary.
 */
class AndroidSpeechSynthesizer
internal constructor(
    private val factory: VoiceEngineFactory,
    private val player: LocalAudioPlayer,
    private val dispatcher: CoroutineDispatcher,
    private val nanoTime: () -> Long,
    private val diagnostic: (SpeechDiagnostic) -> Unit,
) : SpeechSynthesizer, SpeechPlayback {
    constructor(
        context: Context,
        diagnostic: (SpeechDiagnostic) -> Unit = {},
    ) : this(
        AndroidTtsFactory(context.applicationContext) { safely(diagnostic, it) },
        AndroidAudioPlayer(context.applicationContext) { safely(diagnostic, it) },
        Dispatchers.Main.immediate,
        System::nanoTime,
        diagnostic,
    )

    override val description =
        CapabilityDescription(
            setOf(ExecutionMode.OFFLINE),
            setOf(CapabilityFeature.VOICE_PREFERENCE, CapabilityFeature.SPEECH_RATE),
        )
    private val engines = linkedMapOf<String, VoiceEngine>()
    private var preferredEngine: String? = null
    private var catalogLoaded = false
    private var closed = false
    private var foreground = false
    private var active: Job? = null
    private val operationLock = Mutex()
    private var retained: Retained? = null

    private data class Retained(
        val result: SynthesizedSpeech,
        val pcm: PcmAudio,
        val began: Long,
        var playbackAttempted: Boolean = false,
    )

    /** Refresh by backgrounding/foregrounding after the user installs or changes voices. */
    suspend fun installedVoices(): CapabilityResult<List<InstalledVoice>> = latest {
        loadCatalog()
        CapabilityResult.Success(
            engines.values.flatMap { it.voices }.toList(),
            ExecutionMode.OFFLINE,
        )
    }

    override suspend fun synthesize(
        request: SynthesisRequest
    ): CapabilityResult<SynthesizedSpeech> = latest {
        val began = nanoTime()
        if (request.text.isBlank()) throw SpeechFault(SpeechCode.INPUT_INVALID)
        // Android's documented input ceiling, before any text is passed across the engine boundary.
        if (request.text.length > 4000) throw SpeechFault(SpeechCode.INPUT_TOO_LONG)
        if (request.speakerPreservation != SpeakerPreservation.DISABLED)
            throw SpeechFault(SpeechCode.PREFERENCE_UNSUPPORTED)
        if (request.rate !in 0.25..4.0) throw SpeechFault(SpeechCode.RATE_UNSUPPORTED)
        loadCatalog()
        val voice =
            VoicePolicy.select(
                engines.values.flatMap { it.voices },
                request.language,
                request.voicePreference,
                preferredEngine,
            )
                ?: throw SpeechFault(
                    if (request.voicePreference != null) SpeechCode.PREFERENCE_UNSUPPORTED
                    else SpeechCode.OFFLINE_VOICE_MISSING
                )
        emit(
            SpeechDiagnostic(
                SpeechStage.VOICE_SELECTED,
                safeIdentifier(voice.engine),
                safeIdentifier(voice.name),
                voice.locale.tag,
                voice.requiresNetwork,
            )
        )
        emit(SpeechDiagnostic(SpeechStage.SYNTHESIS_START))
        val synthesisBegan = nanoTime()
        val pcm = engines.getValue(voice.engine).synthesize(request.text, voice, request.rate)
        currentCoroutineContext().ensureActive()
        val result =
            SynthesizedSpeech(
                AudioReference.of("a_" + UUID.randomUUID().toString().replace("-", "")),
                voice.locale,
                voice.id,
            )
        retained?.pcm?.bytes?.fill(0)
        retained = Retained(result, pcm, began)
        emit(
            SpeechDiagnostic(
                SpeechStage.SYNTHESIS_COMPLETE,
                milliseconds = (nanoTime() - synthesisBegan) / 1e6,
            )
        )
        CapabilityResult.Success(result, ExecutionMode.OFFLINE)
    }

    override suspend fun play(audio: AudioReference): CapabilityResult<SpeechPlaybackReceipt> =
        latest(SpeechCode.PLAYBACK_FAILED) {
            val cached =
                retained?.takeIf { it.result.audio == audio }
                    ?: throw SpeechFault(SpeechCode.AUDIO_EXPIRED)
            val requested = if (cached.playbackAttempted) nanoTime() else cached.began
            cached.playbackAttempted = true
            val receipt = player.play(cached.pcm, requested)
            currentCoroutineContext().ensureActive()
            emit(
                SpeechDiagnostic(
                    SpeechStage.PLAYBACK_COMPLETE,
                    milliseconds = receipt.requestToAudioMilliseconds,
                    frames = receipt.playbackFramesObserved,
                )
            )
            CapabilityResult.Success(receipt, ExecutionMode.OFFLINE)
        }

    suspend fun setForeground(value: Boolean) =
        withContext(dispatcher + NonCancellable) {
            if (!closed) {
                foreground = value
                if (!value) {
                    cancel()
                    shutdownEngines()
                }
            }
        }

    override suspend fun cancel() =
        withContext(dispatcher + NonCancellable) {
            val previous = active
            previous?.cancelAndJoin()
            if (active === previous) active = null
            operationLock.withLock { if (active == null) stop() }
        }

    override suspend fun release(audio: AudioReference) =
        withContext(dispatcher + NonCancellable) {
            val original = retained?.takeIf { it.result.audio == audio }
            if (original != null) {
                cancel()
                if (retained === original) {
                    original.pcm.bytes.fill(0)
                    retained = null
                }
            }
        }

    suspend fun close() =
        withContext(dispatcher + NonCancellable) {
            if (!closed) {
                closed = true
                cancel()
                shutdownEngines()
                retained?.pcm?.bytes?.fill(0)
                retained = null
                emit(SpeechDiagnostic(SpeechStage.CLOSED))
            }
        }

    private suspend fun loadCatalog() {
        if (catalogLoaded) return
        try {
            val catalog = factory.catalog()
            preferredEngine = catalog.preferredEngine
            for (id in catalog.engines.sortedBy { if (it == preferredEngine) "" else it }) {
                try {
                    engines[id] = factory.open(id)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    emit(
                        SpeechDiagnostic(
                            SpeechStage.FAILED,
                            engine = safeIdentifier(id),
                            code = SpeechCode.ENGINE_UNAVAILABLE,
                            androidCode = (error as? SpeechFault)?.androidCode,
                        )
                    )
                }
            }
            if (engines.isEmpty()) throw SpeechFault(SpeechCode.ENGINE_UNAVAILABLE)
            catalogLoaded = true
        } catch (error: Exception) {
            shutdownEngines()
            throw error
        }
    }

    private suspend fun <T> latest(
        unexpected: SpeechCode = SpeechCode.SYNTHESIS_FAILED,
        action: suspend () -> CapabilityResult<T>,
    ): CapabilityResult<T> =
        withContext(dispatcher) {
            coroutineScope {
                val job = currentCoroutineContext().job
                val previous = active
                active = job
                previous?.cancel(CancellationException("Speech replaced"))
                operationLock.withLock {
                    stop()
                    try {
                        if (closed) throw SpeechFault(SpeechCode.CLOSED)
                        if (!foreground) throw SpeechFault(SpeechCode.BACKGROUND)
                        action()
                    } catch (error: CancellationException) {
                        emit(SpeechDiagnostic(SpeechStage.CANCELLED))
                        throw error
                    } catch (error: Exception) {
                        val fault = if (error is SpeechFault) error else SpeechFault(unexpected)
                        emit(
                            SpeechDiagnostic(
                                SpeechStage.FAILED,
                                code = fault.code,
                                androidCode = fault.androidCode,
                            )
                        )
                        CapabilityResult.Failure(
                            CapabilityFailure(
                                fault.code.category,
                                fault.code.name + ": " + fault.code.message,
                            )
                        )
                    } finally {
                        stop()
                        if (active === job) active = null
                    }
                }
            }
        }

    private fun stop() {
        engines.values.forEach {
            try {
                it.stop()
            } catch (_: Exception) {}
        }
        player.stop()
    }

    private fun shutdownEngines() {
        engines.values.forEach {
            try {
                it.close()
            } catch (_: Exception) {}
        }
        engines.clear()
        catalogLoaded = false
    }

    private fun emit(event: SpeechDiagnostic) = safely(diagnostic, event)

    private companion object {
        fun safely(sink: (SpeechDiagnostic) -> Unit, event: SpeechDiagnostic) {
            // Diagnostics must not fail or retain the speech operation.
            try {
                sink(event)
            } catch (_: Exception) {}
        }
    }
}
