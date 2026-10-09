package com.commontongue.spike.device

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Parser for the existing TTS PCM contract, separate from Whisper's input decoder. */
internal object TtsWav {
    data class Pcm(
        val bytes: ByteArray,
        val rate: Int,
        val channels: Int,
        val firstSignalFrame: Long,
    )

    fun read(bytes: ByteArray, format: (Int, Int, Int, Int) -> Unit): Pcm {
        fun requireWav(condition: Boolean, code: TtsCode = TtsCode.TTS_WAV_INVALID) {
            if (!condition) throw TtsFailure(code)
        }
        requireWav(bytes.size in 45..(32 * 1024 * 1024))
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        requireWav(
            String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE"
        )
        val declared = data.getInt(4).toLong() and 0xffffffffL
        requireWav(declared + 8 <= bytes.size && declared >= 36)
        data.limit((declared + 8).toInt())
        data.position(12)
        var channels = 0
        var rate = 0
        var pcm: ByteArray? = null
        while (data.remaining() >= 8) {
            val name = ByteArray(4).also { data.get(it) }.toString(Charsets.US_ASCII)
            val size = data.int
            requireWav(size >= 0 && size <= data.remaining())
            val end = data.position() + size
            if (name == "fmt ") {
                requireWav(size >= 16)
                val encoding = data.short.toInt() and 0xffff
                channels = data.short.toInt() and 0xffff
                rate = data.int
                data.int
                data.short
                val bits = data.short.toInt() and 0xffff
                format(encoding, rate, channels, bits)
                requireWav(
                    encoding == 1 && channels in 1..2 && rate in 8000..48000 && bits == 16,
                    TtsCode.TTS_WAV_UNSUPPORTED,
                )
            } else if (name == "data") {
                requireWav(channels > 0 && size > 0 && size % (channels * 2) == 0)
                pcm = ByteArray(size).also { data.get(it) }
                break
            }
            requireWav(end.toLong() + size % 2 <= data.limit())
            data.position(end + size % 2)
        }
        val samples = pcm ?: throw TtsFailure(TtsCode.TTS_WAV_INVALID)
        val amplitudes = ByteBuffer.wrap(samples).order(ByteOrder.LITTLE_ENDIAN)
        var firstSignalFrame = 0L
        while (amplitudes.remaining() >= channels * 2) {
            var loudest = 0
            repeat(channels) { loudest = maxOf(loudest, kotlin.math.abs(amplitudes.short.toInt())) }
            if (loudest > 64) return Pcm(samples, rate, channels, firstSignalFrame)
            firstSignalFrame++
        }
        throw TtsFailure(TtsCode.TTS_WAV_SILENT)
    }
}
