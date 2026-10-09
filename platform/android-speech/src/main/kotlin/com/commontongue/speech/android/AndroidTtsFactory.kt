package com.commontongue.speech.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.commontongue.domain.LanguageId
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal class AndroidTtsFactory(
    private val context: Context,
    private val diagnostic: (SpeechDiagnostic) -> Unit,
) : VoiceEngineFactory {
    private val main = Handler(Looper.getMainLooper())

    override suspend fun catalog(): EngineCatalog {
        val discovered =
            context.packageManager
                .queryIntentServices(
                    Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
                    0,
                )
                .map { it.serviceInfo.packageName }
                .distinct()
        return try {
            val bootstrap = initialize(null)
            try {
                EngineCatalog(
                    bootstrap.defaultEngine,
                    (bootstrap.engines.map { it.name } + discovered).distinct(),
                )
            } finally {
                bootstrap.shutdown()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            EngineCatalog(null, discovered)
        }
    }

    override suspend fun open(engine: String): VoiceEngine {
        val began = System.nanoTime()
        val tts = initialize(engine)
        try {
            val voices =
                tts.voices.orEmpty().mapNotNull { voice ->
                    val language =
                        try {
                            LanguageId.parse(voice.locale.toLanguageTag())
                        } catch (_: IllegalArgumentException) {
                            return@mapNotNull null
                        }
                    InstalledVoice(
                        engine,
                        voice.name,
                        language,
                        voice.isNetworkConnectionRequired,
                        !voice.features
                            .orEmpty()
                            .contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED),
                        voice.quality,
                        voice.latency,
                    )
                }
            diagnostic(
                SpeechDiagnostic(
                    SpeechStage.ENGINE_INITIALIZED,
                    engine = safeIdentifier(engine),
                    milliseconds = (System.nanoTime() - began) / 1e6,
                    androidCode = TextToSpeech.SUCCESS,
                )
            )
            return AndroidVoiceEngine(tts, engine, voices, context, main)
        } catch (error: Exception) {
            tts.shutdown()
            throw error
        }
    }

    private suspend fun initialize(engine: String?): TextToSpeech =
        withTimeoutOrNull(15000) {
            suspendCancellableCoroutine { continuation ->
                var instance: TextToSpeech? = null
                val listener = TextToSpeech.OnInitListener { status ->
                    // Android may call back before construction returns; always post.
                    main.post {
                        val ready = instance
                        if (!continuation.isActive) ready?.shutdown()
                        else if (status == TextToSpeech.SUCCESS && ready != null)
                            continuation.resume(ready)
                        else {
                            ready?.shutdown()
                            continuation.resumeWithException(
                                SpeechFault(SpeechCode.ENGINE_UNAVAILABLE, status)
                            )
                        }
                    }
                }
                continuation.invokeOnCancellation { main.post { instance?.shutdown() } }
                instance =
                    if (engine == null) TextToSpeech(context, listener)
                    else TextToSpeech(context, listener, engine)
            }
        } ?: throw SpeechFault(SpeechCode.ENGINE_UNAVAILABLE)
}

private class AndroidVoiceEngine(
    private val tts: TextToSpeech,
    override val engine: String,
    override val voices: List<InstalledVoice>,
    context: Context,
    private val main: Handler,
) : VoiceEngine {
    private val directory = File(context.cacheDir, "offline-speech").apply { mkdirs() }
    private var closed = false

    override suspend fun synthesize(text: String, voice: InstalledVoice, rate: Double): PcmAudio {
        if (closed) throw SpeechFault(SpeechCode.CLOSED)
        val selected =
            tts.voices.orEmpty().firstOrNull {
                it.name == voice.name && it.locale.toLanguageTag() == voice.locale.tag
            } ?: throw SpeechFault(SpeechCode.OFFLINE_VOICE_MISSING)
        if (
            selected.isNetworkConnectionRequired ||
                selected.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        )
            throw SpeechFault(SpeechCode.VOICE_UNVERIFIED)
        if (tts.isLanguageAvailable(selected.locale) < 0 || tts.setLanguage(selected.locale) < 0)
            throw SpeechFault(SpeechCode.OFFLINE_VOICE_MISSING)
        val applied = tts.setVoice(selected)
        val actual = tts.voice
        if (
            applied != TextToSpeech.SUCCESS ||
                actual == null ||
                actual.name != selected.name ||
                actual.locale != selected.locale ||
                actual.isNetworkConnectionRequired ||
                actual.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        )
            throw SpeechFault(SpeechCode.VOICE_UNVERIFIED, applied)
        if (tts.setSpeechRate(rate.toFloat()) != TextToSpeech.SUCCESS)
            throw SpeechFault(SpeechCode.RATE_UNSUPPORTED)
        val id = UUID.randomUUID().toString()
        val file = File(directory, "$id.wav")
        withContext(Dispatchers.IO) {
            // Remove orphaned temporary audio from interrupted/crashed prior sessions only.
            directory
                .listFiles()
                ?.filter {
                    it.extension == "wav" &&
                        System.currentTimeMillis() - it.lastModified() > 3600000
                }
                ?.forEach { it.delete() }
        }
        try {
            val completed =
                withTimeoutOrNull(60000) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        fun finish(error: SpeechFault?) {
                            main.post {
                                if (continuation.isActive) {
                                    if (error == null) continuation.resume(Unit)
                                    else continuation.resumeWithException(error)
                                }
                            }
                        }
                        val result =
                            tts.setOnUtteranceProgressListener(
                                object : UtteranceProgressListener() {
                                    override fun onStart(utteranceId: String?) {}

                                    override fun onDone(utteranceId: String?) {
                                        if (utteranceId == id) finish(null)
                                    }

                                    @Deprecated("Required abstract callback")
                                    override fun onError(utteranceId: String?) =
                                        onError(utteranceId, TextToSpeech.ERROR)

                                    override fun onError(utteranceId: String?, errorCode: Int) {
                                        if (utteranceId == id)
                                            finish(
                                                SpeechFault(SpeechCode.SYNTHESIS_FAILED, errorCode)
                                            )
                                    }

                                    override fun onStop(
                                        utteranceId: String?,
                                        interrupted: Boolean,
                                    ) {
                                        if (utteranceId == id)
                                            finish(SpeechFault(SpeechCode.SYNTHESIS_FAILED))
                                    }
                                }
                            )
                        if (result != TextToSpeech.SUCCESS)
                            finish(SpeechFault(SpeechCode.SYNTHESIS_FAILED, result))
                        else {
                            @Suppress("DEPRECATION")
                            val parameters =
                                Bundle().apply {
                                    putString(
                                        TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS,
                                        "true",
                                    )
                                }
                            val queued = tts.synthesizeToFile(text, parameters, file, id)
                            if (queued != TextToSpeech.SUCCESS)
                                finish(SpeechFault(SpeechCode.SYNTHESIS_FAILED, queued))
                        }
                    }
                }
            if (completed == null) throw SpeechFault(SpeechCode.SYNTHESIS_TIMEOUT)
            return withContext(Dispatchers.IO) {
                if (file.length() !in 45..PcmWav.MAX_BYTES.toLong())
                    throw SpeechFault(SpeechCode.WAV_INVALID)
                PcmWav.read(file.readBytes())
            }
        } catch (error: CancellationException) {
            throw error
        } finally {
            try {
                stop()
            } finally {
                file.delete()
            }
        }
    }

    override fun stop() {
        if (!closed)
            try {
                tts.stop()
            } catch (_: Exception) {}
    }

    override fun close() {
        if (!closed) {
            closed = true
            try {
                tts.stop()
            } finally {
                tts.shutdown()
            }
        }
    }
}
