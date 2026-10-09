package com.commontongue.translation

import com.commontongue.domain.LanguageId
import com.commontongue.domain.TranslationDirection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Input and destination are replaceable ports; a conversation needs only one local device. */
interface ConversationInput {
    suspend fun capture(released: Deferred<Unit>, started: () -> Unit): AudioReference

    suspend fun discard(audio: AudioReference)
}

interface TranslationDestination {
    suspend fun play(audio: AudioReference): CapabilityResult<SpeechPlaybackReceipt>

    suspend fun cancel()

    suspend fun release(audio: AudioReference)
}

class LocalSpeaker(private val playback: SpeechPlayback) : TranslationDestination {
    override suspend fun play(audio: AudioReference) = playback.play(audio)

    override suspend fun cancel() = playback.cancel()

    override suspend fun release(audio: AudioReference) = playback.release(audio)
}

interface ConversationResources {
    suspend fun prepare(): CapabilityResult<Unit>

    suspend fun setForeground(value: Boolean)

    suspend fun close()
}

enum class TalkSide(val source: LanguageId, val target: LanguageId) {
    ENGLISH(LanguageId.parse("en"), LanguageId.parse("es")),
    SPANISH(LanguageId.parse("es"), LanguageId.parse("en"));

    val direction
        get() = TranslationDirection(source, target)
}

enum class TurnStage {
    IDLE,
    LOADING_MODELS,
    STARTING,
    LISTENING,
    RECOGNIZING,
    TRANSLATING,
    SYNTHESIZING,
    SPEAKING,
    COMPLETE,
    CANCELLED,
    ERROR,
    RESOURCES_REQUIRED,
}

enum class TurnProblem {
    RESOURCES_MISSING,
    PREPARATION_FAILED,
    MICROPHONE_DENIED,
    MICROPHONE_SETTINGS,
    MICROPHONE_UNAVAILABLE,
    NO_SPEECH,
    RECOGNITION_FAILED,
    TRANSLATION_FAILED,
    OFFLINE_VOICE_UNAVAILABLE,
    SYNTHESIS_FAILED,
    PLAYBACK_FAILED,
    AUDIO_UNAVAILABLE,
    OFFLINE_REQUIRED,
    PARTIAL_RECOGNITION,
    PARTIAL_TRANSLATION,
    BACKGROUND,
}

/** Safe finite diagnosis, never an exception message or speech content. */
class CaptureFailure(val problem: TurnProblem) : Exception()

data class RecentTurn(
    val side: TalkSide,
    val recognized: String,
    val translated: String,
)

data class ConversationState(
    val stage: TurnStage = TurnStage.IDLE,
    val modelsReady: Boolean = false,
    val side: TalkSide? = null,
    val recognized: String = "",
    val translated: String = "",
    val recent: List<RecentTurn> = emptyList(),
    val replayAvailable: Boolean = false,
    val problem: TurnProblem? = null,
)

/** Exportable by an internal tester surface. Deliberately contains no text, paths, or handles. */
data class TurnDiagnostic(val stage: TurnStage, val problem: TurnProblem? = null)

/**
 * Application turn owner, confined to the caller's UI dispatcher. Generation checks supplement
 * cooperative cancellation; a replacement waits for all old capture/inference/playback cleanup.
 * History is volatile presentation only and is never used as translation context.
 */
class ConversationTurnCoordinator(
    private val scope: CoroutineScope,
    private val input: ConversationInput,
    private val recognizer: SpeechRecognizer,
    private val translator: Translator,
    private val synthesizer: SpeechSynthesizer,
    private val destination: TranslationDestination,
    private val resources: ConversationResources,
    private val diagnostic: (TurnDiagnostic) -> Unit = {},
) {
    private val mutable = MutableStateFlow(ConversationState())
    val state: StateFlow<ConversationState> = mutable.asStateFlow()
    private var generation = 0L
    private var active: Job? = null
    private var releaseSignal: CompletableDeferred<Unit>? = null
    private var replayAudio: AudioReference? = null
    private var foreground = false
    private var closed = false

    fun foreground(value: Boolean) {
        if (closed) return
        foreground = value
        replace {
            resources.setForeground(value)
            if (value) prepareModels()
            else {
                revokeReplay()
                publish(
                    state.value.copy(
                        stage = TurnStage.CANCELLED,
                        modelsReady = false,
                        replayAvailable = false,
                        problem = TurnProblem.BACKGROUND,
                    )
                )
            }
        }
    }

    fun prepare() {
        if (closed || !foreground) return
        replace { prepareModels() }
    }

    private suspend fun prepareModels() {
        publish(
            state.value.copy(stage = TurnStage.LOADING_MODELS, modelsReady = false, problem = null)
        )
        when (val result = resources.prepare()) {
            is CapabilityResult.Success -> {
                currentCoroutineContext().ensureActive()
                if (result.executionMode != ExecutionMode.OFFLINE)
                    fail(TurnProblem.OFFLINE_REQUIRED)
                publish(
                    state.value.copy(stage = TurnStage.IDLE, modelsReady = true, problem = null)
                )
            }
            is CapabilityResult.Failure -> {
                currentCoroutineContext().ensureActive()
                val missing = result.failure.category == FailureCategory.MODEL_NOT_INSTALLED
                publish(
                    state.value.copy(
                        stage = if (missing) TurnStage.RESOURCES_REQUIRED else TurnStage.ERROR,
                        modelsReady = false,
                        problem =
                            if (missing) TurnProblem.RESOURCES_MISSING
                            else TurnProblem.PREPARATION_FAILED,
                    )
                )
            }
        }
    }

    /** The token associates a release with exactly its own press, including rapid replacement. */
    fun press(side: TalkSide): Long? {
        if (closed || !foreground || !state.value.modelsReady) return null
        val signal = CompletableDeferred<Unit>()
        releaseSignal = signal
        val token = replace { ownToken ->
            if (
                listOf(recognizer, translator, synthesizer).any {
                    ExecutionMode.OFFLINE !in it.description.executionModes
                }
            )
                fail(TurnProblem.OFFLINE_REQUIRED)
            destination.cancel() // No deliberate synthesized playback during microphone capture.
            revokeReplay()
            publish(
                state.value.copy(
                    stage = TurnStage.STARTING,
                    side = side,
                    recognized = "",
                    translated = "",
                    replayAvailable = false,
                    problem = null,
                )
            )
            value(resources.prepare(), TurnProblem.PREPARATION_FAILED)
            var audio: AudioReference? = null
            try {
                audio =
                    input.capture(signal) {
                        if (ownToken == generation)
                            publish(state.value.copy(stage = TurnStage.LISTENING))
                    }
                currentCoroutineContext().ensureActive()
                publish(state.value.copy(stage = TurnStage.RECOGNIZING))
                val speech =
                    value(
                        recognizer.recognize(
                            RecognitionRequest(
                                audio,
                                LanguageSelection.Locked(side.source),
                                execution = ExecutionRequirement.OFFLINE_REQUIRED,
                            )
                        ),
                        TurnProblem.RECOGNITION_FAILED,
                    )
                if (speech.text.isBlank()) fail(TurnProblem.NO_SPEECH)
                if (speech.languageUsed.baseLanguage != side.source.baseLanguage)
                    fail(TurnProblem.RECOGNITION_FAILED)
                publish(state.value.copy(recognized = speech.text))
                if (speech.completion != CompletionStatus.COMPLETE)
                    fail(TurnProblem.PARTIAL_RECOGNITION)
                // The private recording is no longer needed after recognition.
                input.discard(audio)
                audio = null
                translateAndSpeak(side, speech.text)
            } finally {
                withContext(NonCancellable) { audio?.let { input.discard(it) } }
            }
        }
        return token
    }

    fun release(token: Long) {
        if (token == generation) releaseSignal?.complete(Unit)
    }

    fun cancelPress(token: Long) {
        if (token == generation) cancel()
    }

    private suspend fun translateAndSpeak(side: TalkSide, text: String) {
        publish(state.value.copy(stage = TurnStage.TRANSLATING))
        val translated =
            value(
                translator.translate(
                    TranslationRequest(
                        text,
                        side.direction,
                        execution = ExecutionRequirement.OFFLINE_REQUIRED,
                    )
                ),
                TurnProblem.TRANSLATION_FAILED,
            )
        if (translated.directionUsed != side.direction || translated.text.isBlank())
            fail(TurnProblem.TRANSLATION_FAILED)
        publish(state.value.copy(translated = translated.text))
        if (translated.completion != CompletionStatus.COMPLETE)
            fail(TurnProblem.PARTIAL_TRANSLATION)
        publish(state.value.copy(stage = TurnStage.SYNTHESIZING))
        val speech =
            value(
                synthesizer.synthesize(
                    SynthesisRequest(
                        translated.text,
                        side.target,
                        execution = ExecutionRequirement.OFFLINE_REQUIRED,
                    )
                ),
                TurnProblem.SYNTHESIS_FAILED,
            )
        if (
            speech.languageUsed.baseLanguage != side.target.baseLanguage ||
                speech.completion != CompletionStatus.COMPLETE
        ) {
            withContext(NonCancellable) { destination.release(speech.audio) }
            fail(TurnProblem.SYNTHESIS_FAILED)
        }
        replayAudio = speech.audio
        publish(state.value.copy(stage = TurnStage.SPEAKING, replayAvailable = true))
        value(destination.play(speech.audio), TurnProblem.PLAYBACK_FAILED)
        val turn = RecentTurn(side, text, translated.text)
        publish(
            state.value.copy(
                stage = TurnStage.COMPLETE,
                recent = (state.value.recent + turn).takeLast(8),
            )
        )
    }

    fun replay() {
        if (closed || !foreground) return
        val audio = replayAudio
        if (audio == null) {
            publish(
                state.value.copy(stage = TurnStage.ERROR, problem = TurnProblem.AUDIO_UNAVAILABLE)
            )
            return
        }
        replace {
            destination.cancel()
            publish(state.value.copy(stage = TurnStage.SPEAKING, problem = null))
            value(destination.play(audio), TurnProblem.PLAYBACK_FAILED)
            publish(state.value.copy(stage = TurnStage.COMPLETE))
        }
    }

    fun permissionDenied(permanent: Boolean) {
        if (closed) return
        replace {
            destination.cancel()
            revokeReplay()
            publish(
                state.value.copy(
                    stage = TurnStage.ERROR,
                    replayAvailable = false,
                    problem =
                        if (permanent) TurnProblem.MICROPHONE_SETTINGS
                        else TurnProblem.MICROPHONE_DENIED,
                )
            )
        }
    }

    fun cancel(clear: Boolean = false) {
        if (closed) return
        replace {
            destination.cancel()
            revokeReplay()
            val ready = state.value.modelsReady
            publish(
                if (clear) ConversationState(modelsReady = ready)
                else
                    state.value.copy(
                        stage = TurnStage.CANCELLED,
                        replayAvailable = false,
                        problem = null,
                    )
            )
        }
    }

    /** Retry synthesis/playback without retaining a microphone recording or adding context. */
    fun retry() {
        if (closed || !foreground) return
        if (!state.value.modelsReady) {
            prepare()
            return
        }
        val side = state.value.side
        val text =
            if (state.value.problem == TurnProblem.PARTIAL_RECOGNITION) ""
            else state.value.recognized
        replace {
            destination.cancel()
            revokeReplay()
            publish(state.value.copy(problem = null, replayAvailable = false))
            if (side != null && text.isNotBlank()) translateAndSpeak(side, text)
            else publish(state.value.copy(stage = TurnStage.IDLE))
        }
    }

    /** Joinable close; caller keeps its cleanup scope alive until this finishes. */
    fun close(): Job {
        closed = true
        foreground = false
        return replace {
            destination.cancel()
            revokeReplay()
            resources.close()
            publish(ConversationState())
        }
            .let { active!! }
    }

    private suspend fun revokeReplay() {
        replayAudio?.let { destination.release(it) }
        replayAudio = null
    }

    private fun replace(action: suspend (Long) -> Unit): Long {
        val token = ++generation
        val previous = active
        previous?.cancel()
        active = scope.launch {
            previous?.join()
            if (token != generation) return@launch
            try {
                action(token)
                currentCoroutineContext().ensureActive()
            } catch (cancelled: CancellationException) {
                // Cancel is control flow. A stale operation never publishes an error or result.
                throw cancelled
            } catch (fault: CaptureFailure) {
                if (token == generation)
                    publish(
                        state.value.copy(
                            stage =
                                if (fault.problem == TurnProblem.RESOURCES_MISSING)
                                    TurnStage.RESOURCES_REQUIRED
                                else TurnStage.ERROR,
                            modelsReady =
                                state.value.modelsReady &&
                                    fault.problem != TurnProblem.RESOURCES_MISSING,
                            problem = fault.problem,
                        )
                    )
            } catch (_: Exception) {
                if (token == generation)
                    publish(
                        state.value.copy(
                            stage = TurnStage.ERROR,
                            problem = TurnProblem.PREPARATION_FAILED,
                        )
                    )
            }
        }
        return token
    }

    private suspend fun <T> value(result: CapabilityResult<T>, fallback: TurnProblem): T {
        currentCoroutineContext().ensureActive()
        return when (result) {
            is CapabilityResult.Success -> {
                if (result.executionMode != ExecutionMode.OFFLINE)
                    fail(TurnProblem.OFFLINE_REQUIRED)
                result.value
            }
            is CapabilityResult.Failure ->
                fail(
                    when (result.failure.category) {
                        FailureCategory.MODEL_NOT_INSTALLED ->
                            if (fallback == TurnProblem.SYNTHESIS_FAILED)
                                TurnProblem.OFFLINE_VOICE_UNAVAILABLE
                            else TurnProblem.RESOURCES_MISSING
                        FailureCategory.OFFLINE_REQUIREMENT_NOT_MET -> TurnProblem.OFFLINE_REQUIRED
                        FailureCategory.LANGUAGE_NOT_SUPPORTED ->
                            if (fallback == TurnProblem.SYNTHESIS_FAILED)
                                TurnProblem.OFFLINE_VOICE_UNAVAILABLE
                            else fallback
                        else -> fallback
                    }
                )
        }
    }

    private fun fail(problem: TurnProblem): Nothing = throw CaptureFailure(problem)

    private fun publish(value: ConversationState) {
        // Actions are confined to the UI dispatcher. Each suspension is checked before publishing.
        mutable.value = value
        try {
            diagnostic(TurnDiagnostic(value.stage, value.problem))
        } catch (_: Exception) {}
    }
}
