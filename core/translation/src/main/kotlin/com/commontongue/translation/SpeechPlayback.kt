package com.commontongue.translation

/** Platform-neutral playback of an ephemeral handle returned by SpeechSynthesizer. */
interface SpeechPlayback {
    /**
     * Finishes after playback; replacing a request or cancelling the caller interrupts playback.
     */
    suspend fun play(audio: AudioReference): CapabilityResult<SpeechPlaybackReceipt>

    /**
     * A new input/translation controller can cancel speech without depending on a particular UI.
     */
    suspend fun cancel()

    /**
     * Revoke replay and release retained audio. Handles are session-local, never filesystem paths.
     */
    suspend fun release(audio: AudioReference)
}

data class SpeechPlaybackReceipt(
    val playbackFramesObserved: Long,
    val requestToAudioMilliseconds: Double,
    val playRequestToAudioMilliseconds: Double,
) {
    init {
        require(playbackFramesObserved > 0)
        require(requestToAudioMilliseconds.isFinite() && requestToAudioMilliseconds >= 0)
        require(playRequestToAudioMilliseconds.isFinite() && playRequestToAudioMilliseconds >= 0)
    }
}
