package com.commontongue.speech.android

import com.commontongue.translation.FailureCategory

enum class SpeechStage {
    ENGINE_INITIALIZED,
    VOICE_SELECTED,
    SYNTHESIS_START,
    SYNTHESIS_COMPLETE,
    PLAYBACK_DETAIL,
    PLAYBACK_START,
    PLAYBACK_COMPLETE,
    CANCELLED,
    FAILED,
    CLOSED,
}

enum class SpeechCode(val category: FailureCategory, val message: String) {
    ENGINE_UNAVAILABLE(
        FailureCategory.ENGINE_NOT_AVAILABLE,
        "No usable speech engine is installed.",
    ),
    OFFLINE_VOICE_MISSING(
        FailureCategory.MODEL_NOT_INSTALLED,
        "Install an offline voice for this language in your phone's speech settings.",
    ),
    VOICE_UNVERIFIED(
        FailureCategory.OFFLINE_REQUIREMENT_NOT_MET,
        "The speech engine could not confirm the selected offline voice.",
    ),
    PREFERENCE_UNSUPPORTED(
        FailureCategory.UNSUPPORTED_CAPABILITY,
        "This voice or speaker preference is unavailable.",
    ),
    RATE_UNSUPPORTED(
        FailureCategory.UNSUPPORTED_CAPABILITY,
        "The speech engine could not apply the requested rate.",
    ),
    INPUT_INVALID(FailureCategory.INPUT_INVALID, "There is no text to read."),
    INPUT_TOO_LONG(FailureCategory.INPUT_TOO_LONG, "This speech request is too long."),
    SYNTHESIS_FAILED(
        FailureCategory.INFERENCE_FAILED,
        "The offline voice could not produce speech.",
    ),
    SYNTHESIS_TIMEOUT(
        FailureCategory.INFERENCE_FAILED,
        "The offline voice did not finish. Try again.",
    ),
    WAV_INVALID(
        FailureCategory.INFERENCE_FAILED,
        "The speech engine returned unsupported or damaged audio.",
    ),
    AUDIO_EXPIRED(FailureCategory.INPUT_INVALID, "This replay is no longer available."),
    FOCUS_DENIED(
        FailureCategory.RESOURCE_LIMIT,
        "Audio is busy. Return to Common Tongue and try again.",
    ),
    FOCUS_LOST(FailureCategory.CANCELLED, "Speech stopped because another app needs audio."),
    ROUTE_DISCONNECTED(
        FailureCategory.CANCELLED,
        "Speech stopped because the audio device disconnected.",
    ),
    PLAYBACK_FAILED(
        FailureCategory.INFERENCE_FAILED,
        "Speech could not play. Check your audio output and try again.",
    ),
    BACKGROUND(FailureCategory.CANCELLED, "Return to Common Tongue to play speech."),
    CLOSED(FailureCategory.ENGINE_NOT_AVAILABLE, "The speech session has ended."),
}

internal class SpeechFault(val code: SpeechCode, val androidCode: Int? = null) :
    Exception(code.name)

/** Typed, bounded metadata only: no text, audio, path, exception message or device identifier. */
data class SpeechDiagnostic(
    val stage: SpeechStage,
    val engine: String? = null,
    val voice: String? = null,
    val locale: String? = null,
    val requiresNetwork: Boolean? = null,
    val milliseconds: Double? = null,
    val frames: Long? = null,
    val code: SpeechCode? = null,
    val androidCode: Int? = null,
    val routeType: Int? = null,
    val playback: PlaybackDiagnostic? = null,
)

internal fun safeIdentifier(value: String): String =
    if (value.matches(Regex("[A-Za-z0-9_.:#-]{1,160}"))) value else "id_" + digest(value)
