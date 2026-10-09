package com.commontongue.spike.device

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.io.File
import java.io.IOException
import java.util.UUID
import org.json.JSONObject

/** Installed offline voice only; diagnostics never contain speech, paths or utterance IDs. */
internal class OfflineSpeech(
    private val context: Context,
    private val diagnostic: (JSONObject) -> Unit,
    private val status: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var initialized = false
    private var initResult: Int? = null
    private var engine = "unknown"
    private var generation = 0
    private var closed = false
    private var track: AudioTrack? = null
    private var pending: File? = null
    private var active: Attempt? = null
    private lateinit var tts: TextToSpeech

    private class Attempt(
        val token: Int,
        val report: JSONObject,
        val failed: (TtsFailure) -> Unit,
    ) {
        val id = UUID.randomUUID().toString() // Engine correlation only.
        var terminal = false
        var timeout: Runnable? = null
    }

    init {
        tts =
            TextToSpeech(context) { result ->
                main.post {
                    if (!closed) {
                        ready = result == TextToSpeech.SUCCESS
                        initialized = true
                        initResult = result
                        if (ready) engine = TtsDiagnostic.identifier(tts.defaultEngine)
                        emit(base("NONE", "NONE"), "ENGINE_INIT", "INITIALIZED")
                        status(readinessMessage())
                    }
                }
            }
    }

    private fun base(operation: String, language: String): JSONObject =
        JSONObject()
            .put("schema_version", 1)
            .put("operation", operation)
            .put("language", language)
            .put("engine", engine)
            .put("engine_initialized", initialized)
            .put("engine_ready", ready)
            .put("init_result", initResult ?: JSONObject.NULL)
            .put("listener_start", false)
            .put("listener_done", false)
            .put("listener_error", false)
            .put("listener_stop", false)
            .put("audio_callback_bytes", 0L)
            .put("audio_callback_chunks", 0L)
            .put("audio_playback_started", false)
            .put("audio_playback_basis", "NOT_OBSERVED")
            .put("speak_result", JSONObject.NULL)
            .put("synthesize_result", JSONObject.NULL)
            .put("android_error_code", JSONObject.NULL)

    private fun emit(report: JSONObject, stage: String, outcome: String = "RUNNING") {
        report
            .put("stage", stage)
            .put("outcome", outcome)
            .put("elapsed_ms", android.os.SystemClock.elapsedRealtime())
        try {
            diagnostic(JSONObject(report.toString()))
        } catch (_: IOException) {
            status("Speech diagnostics could not be saved. Free some storage and try again.")
        }
    }

    fun engineInitialized(): Boolean = initialized

    fun languagesReady(): Boolean =
        ready &&
            try {
                listOf("en", "es").all { voice(it) != null }
            } catch (_: Exception) {
                false
            }

    fun readinessMessage(): String =
        when {
            !initialized -> "Preparing offline speech…"
            !ready -> "Offline speech is unavailable. Tap Set Up Offline Voices."
            !languagesReady() ->
                "An offline English or Spanish voice is missing. Tap Set Up Offline Voices, install both languages, then reopen Common Tongue."
            else -> "Offline English and Spanish voices are available."
        }

    fun openVoiceSetup(activity: Activity) {
        try {
            activity.startActivity(
                Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                    if (ready) setPackage(tts.defaultEngine)
                }
            )
        } catch (_: Exception) {
            try {
                activity.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
            } catch (_: Exception) {
                status(
                    "Open your phone's text-to-speech settings and install offline English and Spanish voices, then reopen Common Tongue."
                )
            }
        }
    }

    private fun voice(language: String): Voice? =
        tts.voices
            .orEmpty()
            .filter {
                it.locale.language == language &&
                    !it.isNetworkConnectionRequired &&
                    !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }
            .sortedWith(
                compareBy<Voice> {
                        if (it.locale.country == if (language == "es") "MX" else "US") 0 else 1
                    }
                    .thenBy { it.name }
            )
            .firstOrNull()

    fun speak(
        text: String,
        language: String,
        released: Long?,
        done: (JSONObject) -> Unit,
        completed: () -> Unit,
        failed: (TtsFailure) -> Unit,
        direct: Boolean = false,
    ) {
        cancel()
        val attempt =
            Attempt(
                generation,
                base(if (direct) "DIRECT_SPEAK" else "SYNTHESIZE_FILE", language),
                failed,
            )
        active = attempt
        val report = attempt.report
        try {
            emit(report, "VOICE_SELECT")
            if (!ready) throw TtsFailure(TtsCode.TTS_NOT_READY)
            val selected = voice(language) ?: throw TtsFailure(TtsCode.TTS_OFFLINE_VOICE_MISSING)
            report
                .put("voice", TtsDiagnostic.identifier(selected.name))
                .put("locale", TtsDiagnostic.locale(selected.locale.toLanguageTag()))
                .put("requires_network", selected.isNetworkConnectionRequired)
            val available = tts.isLanguageAvailable(selected.locale)
            report.put("language_available_result", available)
            emit(report, "LANGUAGE_CONFIG")
            val languageResult = tts.setLanguage(selected.locale)
            report.put("set_language_result", languageResult)
            emit(report, "LANGUAGE_CONFIG")
            if (available < 0 || languageResult < 0)
                throw TtsFailure(TtsCode.TTS_LANGUAGE_UNAVAILABLE)
            // setLanguage can select a default voice: reapply and verify our offline voice.
            emit(report, "VOICE_CONFIG")
            val voiceResult = tts.setVoice(selected)
            report.put("set_voice_result", voiceResult)
            val actual = tts.voice
            report
                .put("active_voice", TtsDiagnostic.identifier(actual?.name))
                .put("active_locale", TtsDiagnostic.locale(actual?.locale?.toLanguageTag()))
                .put(
                    "active_requires_network",
                    actual?.isNetworkConnectionRequired ?: JSONObject.NULL,
                )
                .put("active_voice_matches", actual?.name == selected.name)
            emit(report, "VOICE_CONFIG")
            if (voiceResult != TextToSpeech.SUCCESS) throw TtsFailure(TtsCode.TTS_SET_VOICE_FAILED)
            if (
                actual == null ||
                    actual.name != selected.name ||
                    actual.locale.language != language ||
                    actual.isNetworkConnectionRequired ||
                    actual.features
                        .orEmpty()
                        .contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            )
                throw TtsFailure(TtsCode.TTS_ACTIVE_VOICE_UNVERIFIED)

            val start = Measurements.now()
            val timeout = Runnable {
                if (current(attempt)) fail(attempt, TtsFailure(TtsCode.TTS_SYNTHESIS_TIMEOUT))
            }
            attempt.timeout = timeout
            main.postDelayed(timeout, 60000)
            val file =
                if (direct) null
                else
                    File(File(context.filesDir, "speech").apply { mkdirs() }, "${attempt.id}.wav")
                        .also { pending = it }
            val listenerResult =
                tts.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        private fun callback(id: String?, action: () -> Unit) {
                            if (id == attempt.id) main.post { if (current(attempt)) action() }
                        }

                        override fun onStart(utteranceId: String?) =
                            callback(utteranceId) {
                                if (report.getBoolean("listener_start")) return@callback
                                report.put("listener_start", true)
                                if (direct)
                                    report
                                        .put("audio_playback_started", true)
                                        .put("audio_playback_basis", "ENGINE_ON_START")
                                emit(report, "LISTENER_START")
                                if (direct)
                                    done(
                                        JSONObject()
                                            .put("engine", engine)
                                            .put("voice", report.getString("voice"))
                                            .put("locale", report.getString("locale"))
                                            .put("requires_network", false)
                                    )
                            }

                        override fun onBeginSynthesis(
                            utteranceId: String?,
                            sampleRateInHz: Int,
                            audioFormat: Int,
                            channelCount: Int,
                        ) =
                            callback(utteranceId) {
                                report
                                    .put("engine_sample_rate_hz", sampleRateInHz)
                                    .put("engine_audio_format", audioFormat)
                                    .put("engine_channels", channelCount)
                                emit(report, "SYNTHESIS_FORMAT")
                            }

                        override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
                            val bytes =
                                audio?.size
                                    ?: 0 // Metadata only; the posted action does not capture audio.
                            callback(utteranceId) {
                                report
                                    .put(
                                        "audio_callback_bytes",
                                        report.getLong("audio_callback_bytes") + bytes,
                                    )
                                    .put(
                                        "audio_callback_chunks",
                                        report.getLong("audio_callback_chunks") + 1,
                                    )
                            }
                        }

                        override fun onDone(utteranceId: String?) =
                            callback(utteranceId) {
                                if (report.getBoolean("listener_done")) return@callback
                                report
                                    .put("listener_done", true)
                                    .put("synthesis_ms", Measurements.milliseconds(start))
                                emit(report, "LISTENER_DONE")
                                main.removeCallbacks(timeout)
                                if (direct) {
                                    if (!report.getBoolean("listener_start"))
                                        fail(attempt, TtsFailure(TtsCode.TTS_PLAYBACK_START_FAILED))
                                    else succeed(attempt, completed)
                                } else
                                    try {
                                        play(
                                            file!!,
                                            attempt,
                                            released,
                                            JSONObject()
                                                .put("engine", engine)
                                                .put("voice", report.getString("voice"))
                                                .put("locale", report.getString("locale"))
                                                .put("requires_network", false)
                                                .put(
                                                    "synthesis_ms",
                                                    Measurements.milliseconds(start),
                                                ),
                                            done,
                                            completed,
                                        )
                                    } catch (error: Exception) {
                                        fail(attempt, classify(error))
                                    }
                            }

                        @Deprecated("Required abstract callback")
                        override fun onError(utteranceId: String?) =
                            onError(utteranceId, TextToSpeech.ERROR)

                        override fun onError(utteranceId: String?, errorCode: Int) =
                            callback(utteranceId) {
                                report
                                    .put("listener_error", true)
                                    .put("android_error_code", errorCode)
                                emit(report, "LISTENER_ERROR")
                                fail(attempt, TtsFailure(TtsCode.TTS_ENGINE_ERROR))
                            }

                        override fun onStop(utteranceId: String?, interrupted: Boolean) =
                            callback(utteranceId) {
                                report.put("listener_stop", true)
                                emit(report, "LISTENER_STOP")
                                fail(attempt, TtsFailure(TtsCode.TTS_ENGINE_STOPPED))
                            }
                    }
                )
            report.put("listener_registration_result", listenerResult)
            emit(report, "LISTENER_CONFIG")
            if (listenerResult != TextToSpeech.SUCCESS)
                throw TtsFailure(TtsCode.TTS_LISTENER_FAILED)
            val enqueue =
                if (direct) tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), attempt.id)
                else tts.synthesizeToFile(text, Bundle(), file!!, attempt.id)
            report.put(if (direct) "speak_result" else "synthesize_result", enqueue)
            emit(report, "ENQUEUE")
            if (enqueue != TextToSpeech.SUCCESS) throw TtsFailure(TtsCode.TTS_ENQUEUE_FAILED)
        } catch (error: Exception) {
            fail(attempt, classify(error))
        }
    }

    private fun classify(error: Exception): TtsFailure =
        if (error is TtsFailure) error
        else TtsFailure(TtsCode.TTS_INTEGRATION_EXCEPTION, TtsDiagnostic.errorType(error))

    private fun current(attempt: Attempt): Boolean =
        !closed && !attempt.terminal && attempt.token == generation && active === attempt

    private fun fail(attempt: Attempt, error: TtsFailure) {
        if (!current(attempt)) return
        attempt.report.put("diagnostic_code", error.code.name).put("error_type", error.type)
        emit(attempt.report, attempt.report.optString("stage", "VOICE_SELECT"), "FAILED")
        attempt.terminal = true
        attempt.timeout?.let { main.removeCallbacks(it) }
        active = null
        cancel()
        attempt.failed(error)
    }

    private fun succeed(attempt: Attempt, completed: () -> Unit) {
        if (!current(attempt)) return
        attempt.terminal = true
        attempt.timeout?.let { main.removeCallbacks(it) }
        active = null
        emit(attempt.report, "COMPLETE", "PASS")
        completed()
    }

    private fun play(
        file: File,
        attempt: Attempt,
        released: Long?,
        result: JSONObject,
        done: (JSONObject) -> Unit,
        completed: () -> Unit,
    ) {
        val report = attempt.report
        report.put("wav_bytes", file.length())
        emit(report, "WAV_READ")
        if (file.length() !in 45..(32L * 1024 * 1024)) throw TtsFailure(TtsCode.TTS_WAV_INVALID)
        val bytes = file.readBytes()
        file.delete()
        pending = null
        val pcm =
            TtsWav.read(bytes) { encoding, rate, channels, bits ->
                report
                    .put("wav_encoding", encoding)
                    .put("wav_sample_rate_hz", rate)
                    .put("wav_channels", channels)
                    .put("wav_bits_per_sample", bits)
                emit(report, "WAV_FORMAT")
            }
        val samples = pcm.bytes
        val rate = pcm.rate
        val channels = pcm.channels
        emit(report, "PLAYBACK_INIT")
        val player =
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(
                            if (channels == 1) AudioFormat.CHANNEL_OUT_MONO
                            else AudioFormat.CHANNEL_OUT_STEREO
                        )
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size)
                .build()
        track = player
        report.put("audio_track_state", player.state)
        val written = player.write(samples, 0, samples.size)
        report.put("audio_track_write_result", written)
        emit(report, "PLAYBACK_INIT")
        if (player.state != AudioTrack.STATE_INITIALIZED || written != samples.size)
            throw TtsFailure(TtsCode.TTS_PLAYBACK_INIT_FAILED)
        val requested = Measurements.now()
        player.play()
        emit(report, "PLAYBACK_REQUEST")
        main.post(
            object : Runnable {
                override fun run() {
                    if (!current(attempt)) return
                    try {
                        val timestamp = AudioTimestamp()
                        val observed = Measurements.now()
                        val validTimestamp =
                            player.getTimestamp(timestamp) && timestamp.framePosition > 0
                        val frames = player.playbackHeadPosition.toLong() and 0xffffffffL
                        if (validTimestamp || frames > 0) {
                            val frameZero =
                                if (validTimestamp)
                                    timestamp.nanoTime + (Measurements.now() - System.nanoTime()) -
                                        (timestamp.framePosition * 1e9 / rate).toLong()
                                else observed - (frames * 1e9 / rate).toLong()
                            val signalStart =
                                frameZero + (pcm.firstSignalFrame * 1e9 / rate).toLong()
                            report
                                .put("audio_playback_started", true)
                                .put(
                                    "audio_playback_basis",
                                    if (validTimestamp) "AUDIOTRACK_TIMESTAMP"
                                    else "AUDIOTRACK_HEAD",
                                )
                            emit(report, "PLAYBACK_START")
                            val durationMs = samples.size * 1000.0 / (rate * channels * 2)
                            result
                                .put(
                                    "audio_start_basis",
                                    if (validTimestamp) "AudioTrack_timestamp_plus_PCM_threshold64"
                                    else "playback_head_plus_PCM_threshold64",
                                )
                                .put("first_signal_offset_ms", pcm.firstSignalFrame * 1000.0 / rate)
                                .put(
                                    "release_to_audio_ms",
                                    if (released != null)
                                        (signalStart - released).coerceAtLeast(0) / 1e6
                                    else JSONObject.NULL,
                                )
                                .put(
                                    "play_request_to_audio_ms",
                                    (frameZero - requested).coerceAtLeast(0) / 1e6,
                                )
                                .put("playback_duration_ms", durationMs)
                            done(result)
                            main.post(
                                object : Runnable {
                                    override fun run() {
                                        if (!current(attempt)) return
                                        try {
                                            val played =
                                                player.playbackHeadPosition.toLong() and 0xffffffffL
                                            if (played >= samples.size / (channels * 2)) {
                                                player.stop()
                                                player.release()
                                                track = null
                                                succeed(attempt, completed)
                                            } else if (
                                                Measurements.milliseconds(requested) >
                                                    durationMs + 2000
                                            )
                                                fail(
                                                    attempt,
                                                    TtsFailure(TtsCode.TTS_PLAYBACK_INCOMPLETE),
                                                )
                                            else main.postDelayed(this, 100)
                                        } catch (error: Exception) {
                                            fail(attempt, classify(error))
                                        }
                                    }
                                }
                            )
                        } else if (Measurements.milliseconds(requested) > 2000)
                            fail(attempt, TtsFailure(TtsCode.TTS_PLAYBACK_START_FAILED))
                        else main.postDelayed(this, 10)
                    } catch (error: Exception) {
                        fail(attempt, classify(error))
                    }
                }
            }
        )
    }

    fun cancel() {
        active?.let {
            if (!it.terminal) emit(it.report, "CANCELLED", "CANCELLED")
            it.terminal = true
            it.timeout?.let { timeout -> main.removeCallbacks(timeout) }
        }
        active = null
        generation++
        if (::tts.isInitialized)
            try {
                tts.stop()
            } catch (_: Exception) {}
        pending?.delete()
        pending = null
        track?.let {
            try {
                it.stop()
            } catch (_: Exception) {}
            it.release()
        }
        track = null
    }

    fun close() {
        cancel()
        closed = true
        if (::tts.isInitialized) tts.shutdown()
    }
}
