package com.commontongue.spike.device

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class PcmWavTest {
    private fun wav(frames: Int, rate: Int = 16000, channels: Int = 1): ByteArray {
        val data = ByteBuffer.allocate(44 + frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        data
            .put("RIFF".toByteArray())
            .putInt(36 + frames * 2)
            .put("WAVEfmt ".toByteArray())
            .putInt(16)
            .putShort(1)
            .putShort(channels.toShort())
            .putInt(rate)
            .putInt(rate * channels * 2)
            .putShort((channels * 2).toShort())
            .putShort(16)
            .put("data".toByteArray())
            .putInt(frames * 2)
        repeat(frames) { data.putShort(if (it % 2 == 0) 16384 else -16384) }
        return data.array()
    }

    private fun read(data: ByteArray): FloatArray {
        val file = Files.createTempFile("pcm-test", ".wav").toFile()
        return try {
            file.writeBytes(data)
            readPcm(file)
        } finally {
            file.delete()
        }
    }

    @Test
    fun reportedMicrophoneDurationsPreserveFrameCountAndPcmNormalization() {
        for (frames in listOf(31680, 65600)) {
            val pcm = read(wav(frames))
            assertEquals(frames, pcm.size)
            assertEquals(0.5f, pcm.first(), 0f)
            assertEquals(-0.5f, pcm.last(), 0f)
            assertTrue(pcm.all { it.isFinite() && it in -1f..1f })
        }
    }

    @Test
    fun unsupportedFormatTruncatedDataAndOversizedFileFailBeforeNativeAsr() {
        for (data in
            listOf(
                wav(3200, rate = 48000),
                wav(3200, channels = 2),
                wav(3200).dropLast(1).toByteArray(),
                wav(16000 * 32),
            )) {
            assertThrows(IllegalStateException::class.java) { read(data) }
        }
    }
}
