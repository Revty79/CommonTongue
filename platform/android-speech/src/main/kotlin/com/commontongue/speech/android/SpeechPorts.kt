package com.commontongue.speech.android

import com.commontongue.translation.SpeechPlaybackReceipt

internal interface VoiceEngine {
    val engine: String
    val voices: List<InstalledVoice>

    suspend fun synthesize(text: String, voice: InstalledVoice, rate: Double): PcmAudio

    fun stop()

    fun close()
}

internal data class EngineCatalog(val preferredEngine: String?, val engines: List<String>)

internal interface VoiceEngineFactory {
    suspend fun catalog(): EngineCatalog

    suspend fun open(engine: String): VoiceEngine
}

internal interface LocalAudioPlayer {
    suspend fun play(pcm: PcmAudio, synthesisRequestedNanos: Long): SpeechPlaybackReceipt

    fun stop()
}
