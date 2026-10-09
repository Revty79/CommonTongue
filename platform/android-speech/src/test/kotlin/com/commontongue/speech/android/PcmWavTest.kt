package com.commontongue.speech.android

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class PcmWavTest {
    private fun wav(
        channels: Int = 1,
        bits: Int = 16,
        samples: ShortArray = shortArrayOf(0, 0, 100, -200),
    ): ByteArray {
        val pcm = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { pcm.putShort(it) }
        return ByteBuffer.allocate(44 + pcm.capacity())
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".toByteArray())
                putInt(36 + pcm.capacity())
                put("WAVEfmt ".toByteArray())
                putInt(16)
                putShort(1)
                putShort(channels.toShort())
                putInt(16000)
                putInt(16000 * channels * 2)
                putShort((channels * 2).toShort())
                putShort(bits.toShort())
                put("data".toByteArray())
                putInt(pcm.capacity())
                put(pcm.array())
            }
            .array()
    }

    private fun invalid(bytes: ByteArray) {
        try {
            PcmWav.read(bytes)
            fail("Expected invalid WAV")
        } catch (error: SpeechFault) {
            assertEquals(SpeechCode.WAV_INVALID, error.code)
        }
    }

    @Test
    fun decodesMonoAndStereoAndFindsFirstSignal() {
        assertEquals(2L, PcmWav.read(wav()).firstSignalFrame)
        val stereo = PcmWav.read(wav(2))
        assertEquals(2, stereo.channels)
        assertEquals(1L, stereo.firstSignalFrame)
        assertEquals(16000, stereo.rate)
    }

    @Test
    fun rejectsSilentAudio() {
        invalid(wav(samples = ShortArray(4)))
    }

    @Test
    fun rejectsUnsupportedEncodingAndChannels() {
        invalid(wav(bits = 8))
        invalid(wav(channels = 3))
    }

    @Test
    fun rejectsTruncationAndOversizedChunks() {
        invalid(wav().copyOf(48))
        val bad = wav()
        ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(40, Int.MAX_VALUE)
        invalid(bad)
    }

    @Test
    fun rejectsBrokenAlignmentAndRate() {
        val bad = wav()
        ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putShort(32, 1)
        invalid(bad)
    }

    @Test
    fun skipsOddUnknownChunksWithPadding() {
        val original = wav()
        val chunk = byteArrayOf(74, 85, 78, 75, 1, 0, 0, 0, 7, 0)
        val bytes = original.copyOfRange(0, 12) + chunk + original.copyOfRange(12, original.size)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(4, bytes.size - 8)
        assertEquals(2L, PcmWav.read(bytes).firstSignalFrame)
    }
}
