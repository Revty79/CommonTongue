package com.commontongue.spike.device

/** Finite failure codes and bounded configuration names; never retain engine exception messages. */
internal enum class TtsCode {
    TTS_NOT_READY,
    TTS_OFFLINE_VOICE_MISSING,
    TTS_LANGUAGE_UNAVAILABLE,
    TTS_SET_VOICE_FAILED,
    TTS_ACTIVE_VOICE_UNVERIFIED,
    TTS_LISTENER_FAILED,
    TTS_ENQUEUE_FAILED,
    TTS_ENGINE_ERROR,
    TTS_ENGINE_STOPPED,
    TTS_SYNTHESIS_TIMEOUT,
    TTS_WAV_INVALID,
    TTS_WAV_UNSUPPORTED,
    TTS_WAV_SILENT,
    TTS_PLAYBACK_INIT_FAILED,
    TTS_PLAYBACK_START_FAILED,
    TTS_PLAYBACK_INCOMPLETE,
    TTS_INTEGRATION_EXCEPTION,
}

internal class TtsFailure(val code: TtsCode, val type: String = "TtsFailure") :
    Exception(
        when (code) {
            TtsCode.TTS_NOT_READY ->
                "Offline speech is still preparing. Try again when it is ready."
            TtsCode.TTS_OFFLINE_VOICE_MISSING,
            TtsCode.TTS_LANGUAGE_UNAVAILABLE ->
                "An offline voice is missing. Tap Set Up Offline Voices."
            TtsCode.TTS_SET_VOICE_FAILED,
            TtsCode.TTS_ACTIVE_VOICE_UNVERIFIED ->
                "The phone could not select an installed offline voice. Export Research Results."
            TtsCode.TTS_WAV_INVALID,
            TtsCode.TTS_WAV_UNSUPPORTED,
            TtsCode.TTS_WAV_SILENT ->
                "The speech engine returned audio the app could not play. Export Research Results."
            TtsCode.TTS_PLAYBACK_INIT_FAILED,
            TtsCode.TTS_PLAYBACK_START_FAILED,
            TtsCode.TTS_PLAYBACK_INCOMPLETE ->
                "Speech playback did not finish. Check media volume and export Research Results."
            else -> "Offline speech did not finish. Export Research Results so we can inspect it."
        }
    )

internal object TtsDiagnostic {
    fun identifier(value: String?): String =
        value?.takeIf { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.#-]{0,159}")) } ?: "REDACTED"

    fun locale(value: String?): String =
        value?.takeIf { it.matches(Regex("(en|es)(-[A-Za-z0-9]{1,8})*")) } ?: "REDACTED"

    fun errorType(error: Exception): String =
        error.javaClass.simpleName.takeIf {
            it in
                setOf(
                    "IllegalStateException",
                    "IllegalArgumentException",
                    "IOException",
                    "SecurityException",
                    "NullPointerException",
                    "UnsupportedOperationException",
                )
        } ?: "Exception"
}
