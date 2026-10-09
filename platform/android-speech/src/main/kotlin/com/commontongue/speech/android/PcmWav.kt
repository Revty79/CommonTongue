package com.commontongue.speech.android

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class PcmAudio(
    val bytes: ByteArray,
    val rate: Int,
    val channels: Int,
    val firstSignalFrame: Long,
)

/** Production copy of the proven PCM16 route; does not alter the research decoder. */
internal object PcmWav {
    const val MAX_BYTES = 32 * 1024 * 1024

    fun read(bytes: ByteArray): PcmAudio {
        fun valid(condition: Boolean) {
            if (!condition) throw SpeechFault(SpeechCode.WAV_INVALID)
        }
        valid(bytes.size in 45..MAX_BYTES)
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        valid(
            String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE"
        )
        val declared = data.getInt(4).toLong() and 0xffffffffL
        valid(declared >= 36 && declared + 8 <= bytes.size)
        data.limit((declared + 8).toInt())
        data.position(12)
        var channels = 0
        var rate = 0
        var samples: ByteArray? = null
        var formatted = false
        while (data.remaining() >= 8) {
            val name = ByteArray(4).also { data.get(it) }.toString(Charsets.US_ASCII)
            val size = data.int
            valid(size >= 0 && size <= data.remaining())
            val end = data.position() + size
            if (name == "fmt ") {
                valid(!formatted && size >= 16)
                val encoding = data.short.toInt() and 0xffff
                channels = data.short.toInt() and 0xffff
                rate = data.int
                val byteRate = data.int
                val alignment = data.short.toInt() and 0xffff
                val bits = data.short.toInt() and 0xffff
                valid(encoding == 1 && channels in 1..2 && rate in 8000..48000 && bits == 16)
                valid(alignment == channels * 2 && byteRate == rate * alignment)
                formatted = true
            } else if (name == "data") {
                valid(formatted && size > 0 && size % (channels * 2) == 0)
                samples = ByteArray(size).also { data.get(it) }
                break
            }
            valid(end.toLong() + size % 2 <= data.limit())
            data.position(end + size % 2)
        }
        val pcm = samples ?: throw SpeechFault(SpeechCode.WAV_INVALID)
        val amplitudes = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        var first = 0L
        while (amplitudes.remaining() >= channels * 2) {
            var loudest = 0
            repeat(channels) { loudest = maxOf(loudest, kotlin.math.abs(amplitudes.short.toInt())) }
            if (loudest > 64) return PcmAudio(pcm, rate, channels, first)
            first++
        }
        throw SpeechFault(SpeechCode.WAV_INVALID)
    }
}
