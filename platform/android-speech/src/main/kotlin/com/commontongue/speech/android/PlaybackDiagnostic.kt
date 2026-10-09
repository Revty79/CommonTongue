package com.commontongue.speech.android

import android.media.AudioTrack
import kotlinx.coroutines.CancellationException

enum class PlaybackStep {
    PCM_FORMAT,
    FOCUS_REQUEST,
    NOISY_RECEIVER_REGISTER,
    TRACK_CONSTRUCT,
    ROUTING_LISTENER_REGISTER,
    PCM_WRITE,
    TRACK_VALIDATE,
    PLAY_INVOKE,
    HEAD_INITIAL,
    HEAD_ADVANCE,
    HEAD_COMPLETE,
    WAIT_FOR_COMPLETION,
}

enum class PlaybackOutcome {
    STARTED,
    SUCCEEDED,
    REJECTED,
    TIMED_OUT,
    ANDROID_EXCEPTION,
}

/** Fixed type labels only; exception messages, causes and stack traces never enter diagnostics. */
enum class PlaybackExceptionType {
    SECURITY_EXCEPTION,
    ILLEGAL_ARGUMENT_EXCEPTION,
    ILLEGAL_STATE_EXCEPTION,
    UNSUPPORTED_OPERATION_EXCEPTION,
    RUNTIME_EXCEPTION,
    OTHER_EXCEPTION,
}

data class PlaybackDiagnostic(
    val step: PlaybackStep,
    val outcome: PlaybackOutcome,
    val trackState: Int? = null,
    val playState: Int? = null,
    val pcmBytes: Int? = null,
    val channels: Int? = null,
    val sampleRateHz: Int? = null,
    val focusResult: Int? = null,
    val writeResult: Int? = null,
    val frames: Long? = null,
    val routeType: Int? = null,
    val milliseconds: Double? = null,
    val code: SpeechCode? = null,
    val exceptionType: PlaybackExceptionType? = null,
)

internal fun playbackExceptionType(error: Exception): PlaybackExceptionType =
    when (error) {
        is SecurityException -> PlaybackExceptionType.SECURITY_EXCEPTION
        is IllegalArgumentException -> PlaybackExceptionType.ILLEGAL_ARGUMENT_EXCEPTION
        is IllegalStateException -> PlaybackExceptionType.ILLEGAL_STATE_EXCEPTION
        is UnsupportedOperationException -> PlaybackExceptionType.UNSUPPORTED_OPERATION_EXCEPTION
        is RuntimeException -> PlaybackExceptionType.RUNTIME_EXCEPTION
        else -> PlaybackExceptionType.OTHER_EXCEPTION
    }

internal class PlaybackTrace(private val diagnostic: (SpeechDiagnostic) -> Unit) {
    private var current = PlaybackStep.PCM_FORMAT

    fun begin(step: PlaybackStep) {
        current = step
        record(PlaybackDiagnostic(step, PlaybackOutcome.STARTED))
    }

    fun record(detail: PlaybackDiagnostic) {
        diagnostic(
            SpeechDiagnostic(SpeechStage.PLAYBACK_DETAIL, code = detail.code, playback = detail)
        )
    }

    fun failed(error: Exception) {
        if (error is CancellationException) return
        record(
            PlaybackDiagnostic(
                current,
                if (error is SpeechFault) PlaybackOutcome.REJECTED
                else PlaybackOutcome.ANDROID_EXCEPTION,
                code = (error as? SpeechFault)?.code,
                exceptionType = if (error is SpeechFault) null else playbackExceptionType(error),
            )
        )
    }
}

/**
 * A small test seam for Android's static buffer state transition; the actual write stays in
 * Android.
 */
internal fun loadStaticPcm(
    bytes: Int,
    state: () -> Int,
    write: () -> Int,
    trace: PlaybackTrace,
) {
    // MODE_STATIC starts in STATE_NO_STATIC_DATA. The first write makes it ready,
    // matching the physically accepted Pass 5 path; checking readiness first prevents that write.
    trace.begin(PlaybackStep.PCM_WRITE)
    val written = write()
    val loadedState = state()
    trace.record(
        PlaybackDiagnostic(
            PlaybackStep.PCM_WRITE,
            PlaybackOutcome.SUCCEEDED,
            trackState = loadedState,
            writeResult = written,
        )
    )
    trace.begin(PlaybackStep.TRACK_VALIDATE)
    if (loadedState != AudioTrack.STATE_INITIALIZED || written != bytes)
        throw SpeechFault(SpeechCode.PLAYBACK_FAILED)
    trace.record(
        PlaybackDiagnostic(
            PlaybackStep.TRACK_VALIDATE,
            PlaybackOutcome.SUCCEEDED,
            trackState = loadedState,
        )
    )
}
