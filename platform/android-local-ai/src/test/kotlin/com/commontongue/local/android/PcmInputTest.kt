package com.commontongue.local.android

import com.commontongue.local.LocalFailure
import com.commontongue.local.LocalFault
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class PcmInputTest {
    private fun wav(channels: Short = 1, rate: Int = 16000, bits: Short = 16): ByteArray {
        val bytes = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray())
        bytes.putInt(40)
        bytes.put("WAVEfmt ".toByteArray())
        bytes.putInt(16)
        bytes.putShort(1)
        bytes.putShort(channels)
        bytes.putInt(rate)
        bytes.putInt(rate * channels * 2)
        bytes.putShort((channels * 2).toShort())
        bytes.putShort(bits)
        bytes.put("data".toByteArray())
        bytes.putInt(4)
        bytes.putShort(Short.MIN_VALUE)
        bytes.putShort(Short.MAX_VALUE)
        return bytes.array()
    }

    private fun decode(bytes: ByteArray, valid: Boolean = false) {
        val file = kotlin.io.path.createTempFile().toFile()
        try {
            file.writeBytes(bytes)
            try {
                val samples = PcmInput.read(file)
                assertTrue("Invalid waveform accepted", valid)
                assertArrayEquals(floatArrayOf(-1f, 32767 / 32768f), samples, 0f)
            } catch (error: LocalFault) {
                if (valid) throw error
                assertEquals(LocalFailure.INPUT_INVALID, error.reason)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun signedPcmUsesAcceptedScale() {
        decode(wav(), valid = true)
    }

    @Test
    fun stereoRequiresExplicitConversionRatherThanSilentMix() {
        decode(wav(channels = 2))
    }

    @Test
    fun wrongSampleRateIsRejected() {
        decode(wav(rate = 48000))
    }

    @Test
    fun unsupportedBitDepthIsRejected() {
        decode(wav(bits = 32))
    }

    @Test
    fun truncatedBufferIsRejected() {
        decode(wav().copyOf(46))
    }

    @Test
    fun oversizedChunkIsRejected() {
        val bytes = wav()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(40, Int.MAX_VALUE)
        decode(bytes)
    }

    @Test
    fun missingHeaderIsRejected() {
        decode(ByteArray(48))
    }
}
