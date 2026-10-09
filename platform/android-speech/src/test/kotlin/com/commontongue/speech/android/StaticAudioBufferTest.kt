package com.commontongue.speech.android

import android.media.AudioTrack
import org.junit.Assert.*
import org.junit.Test

class StaticAudioBufferTest {
    private val events = mutableListOf<SpeechDiagnostic>()
    private val trace = PlaybackTrace { events.add(it) }

    @Test
    fun emptyStaticTrackIsWrittenBeforeCheckingReadyState() {
        var state = AudioTrack.STATE_NO_STATIC_DATA
        var writes = 0
        loadStaticPcm(
            128,
            { state },
            {
                writes++
                state = AudioTrack.STATE_INITIALIZED
                128
            },
            trace,
        )
        assertEquals(1, writes)
        assertEquals(AudioTrack.STATE_INITIALIZED, state)
        val steps = events.map { it.playback!!.step }
        assertTrue(
            steps.indexOf(PlaybackStep.PCM_WRITE) < steps.indexOf(PlaybackStep.TRACK_VALIDATE)
        )
        assertEquals(128, events.first { it.playback?.writeResult != null }.playback!!.writeResult)
    }

    @Test
    fun initializedTrackAlsoReceivesTheWholeBuffer() {
        var writes = 0
        loadStaticPcm(
            128,
            { AudioTrack.STATE_INITIALIZED },
            {
                writes++
                128
            },
            trace,
        )
        assertEquals(1, writes)
    }

    private fun rejected(state: Int, written: Int) {
        try {
            loadStaticPcm(128, { state }, { written }, trace)
            fail("Expected playback failure")
        } catch (error: SpeechFault) {
            assertEquals(SpeechCode.PLAYBACK_FAILED, error.code)
        }
    }

    @Test
    fun rejectsPartialWrite() {
        rejected(AudioTrack.STATE_INITIALIZED, 64)
    }

    @Test
    fun rejectsNegativeWrite() {
        rejected(AudioTrack.STATE_INITIALIZED, AudioTrack.ERROR_BAD_VALUE)
    }

    @Test
    fun rejectsZeroWrite() {
        rejected(AudioTrack.STATE_NO_STATIC_DATA, 0)
    }

    @Test
    fun rejectsFullWriteThatDoesNotInitializeTrack() {
        rejected(AudioTrack.STATE_NO_STATIC_DATA, 128)
    }

    @Test
    fun exceptionDiagnosticsContainOnlyFixedTypesAndFailedCheckpoint() {
        trace.begin(PlaybackStep.NOISY_RECEIVER_REGISTER)
        trace.failed(SecurityException("private text /private/path device-id"))
        val result = events.last().playback!!
        assertEquals(PlaybackOutcome.ANDROID_EXCEPTION, result.outcome)
        assertEquals(PlaybackStep.NOISY_RECEIVER_REGISTER, result.step)
        assertEquals(PlaybackExceptionType.SECURITY_EXCEPTION, result.exceptionType)
        assertFalse(events.toString().contains("private"))
        assertEquals(
            PlaybackExceptionType.ILLEGAL_STATE_EXCEPTION,
            playbackExceptionType(IllegalStateException("secret")),
        )
        assertEquals(
            PlaybackExceptionType.OTHER_EXCEPTION,
            playbackExceptionType(Exception("secret")),
        )
    }

    @Test
    fun typedFailureAndTimeoutRemainDistinctFromAndroidExceptions() {
        trace.begin(PlaybackStep.WAIT_FOR_COMPLETION)
        trace.record(
            PlaybackDiagnostic(
                PlaybackStep.WAIT_FOR_COMPLETION,
                PlaybackOutcome.TIMED_OUT,
                frames = 0,
                code = SpeechCode.PLAYBACK_FAILED,
            )
        )
        trace.failed(SpeechFault(SpeechCode.PLAYBACK_FAILED))
        assertEquals(PlaybackOutcome.TIMED_OUT, events[1].playback!!.outcome)
        assertEquals(PlaybackOutcome.REJECTED, events.last().playback!!.outcome)
        assertNull(events.last().playback!!.exceptionType)
    }
}
