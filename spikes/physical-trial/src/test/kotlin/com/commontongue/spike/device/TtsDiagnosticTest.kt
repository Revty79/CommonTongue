package com.commontongue.spike.device

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class TtsDiagnosticTest {
    private fun wav(channels: Int = 1, bits: Int = 16, encoding: Int = 1): ByteArray {
        val samples = shortArrayOf(0, 0, 1000, -1000)
        return ByteBuffer.allocate(44 + samples.size * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".toByteArray())
                putInt(36 + samples.size * 2)
                put("WAVEfmt ".toByteArray())
                putInt(16)
                putShort(encoding.toShort())
                putShort(channels.toShort())
                putInt(22050)
                putInt(22050 * channels * 2)
                putShort((channels * 2).toShort())
                putShort(bits.toShort())
                put("data".toByteArray())
                putInt(samples.size * 2)
                samples.forEach { putShort(it) }
            }
            .array()
    }

    @Test
    fun monoAndStereoPreservePcmAndLeadingSilenceWithoutWhisperDecoder() {
        for (channels in 1..2) {
            val bytes = wav(channels)
            val pcm =
                TtsWav.read(bytes) { encoding, rate, actualChannels, bits ->
                    assertEquals(1, encoding)
                    assertEquals(22050, rate)
                    assertEquals(channels, actualChannels)
                    assertEquals(16, bits)
                }
            assertArrayEquals(bytes.copyOfRange(44, bytes.size), pcm.bytes)
            assertEquals((2 / channels).toLong(), pcm.firstSignalFrame)
        }
    }

    @Test
    fun engineFormatIsReportedBeforeUnsupportedFormatFails() {
        for ((bits, encoding) in listOf(8 to 1, 32 to 3)) {
            var observed = false
            val error =
                assertThrows(TtsFailure::class.java) {
                    TtsWav.read(wav(bits = bits, encoding = encoding)) {
                        actualEncoding,
                        _,
                        _,
                        actualBits ->
                        observed = true
                        assertEquals(bits, actualBits)
                        assertEquals(encoding, actualEncoding)
                    }
                }
            assertTrue(observed)
            assertEquals(TtsCode.TTS_WAV_UNSUPPORTED, error.code)
        }
    }

    @Test
    fun truncatedOversizedChunksAndMissingDataAreClassifiedWithoutBufferExceptions() {
        val corruptChunk =
            wav().also {
                ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(40, Int.MAX_VALUE)
            }
        val missing = wav().also { "junk".toByteArray().copyInto(it, 36) }
        for (bytes in listOf(wav().copyOf(46), corruptChunk, missing)) {
            assertEquals(
                TtsCode.TTS_WAV_INVALID,
                assertThrows(TtsFailure::class.java) { TtsWav.read(bytes) { _, _, _, _ -> } }.code,
            )
        }
    }

    @Test
    fun silentOutputDoesNotClaimPlaybackStarted() {
        val bytes = wav().also { it.fill(0, 44) }
        assertEquals(
            TtsCode.TTS_WAV_SILENT,
            assertThrows(TtsFailure::class.java) { TtsWav.read(bytes) { _, _, _, _ -> } }.code,
        )
    }

    @Test
    fun configurationNamesAndExceptionsCannotCarryPathsHumanTextOrIdentifiers() {
        assertEquals("com.samsung.SMT", TtsDiagnostic.identifier("com.samsung.SMT"))
        assertEquals("es-es-x-local", TtsDiagnostic.identifier("es-es-x-local"))
        assertEquals("es-MX", TtsDiagnostic.locale("es-MX"))
        for (privateValue in
            listOf(
                "/data/user/private",
                "C:\\Users\\tester",
                "hello world",
                "device@10.0.0.1",
                "a".repeat(200),
            )) {
            assertEquals("REDACTED", TtsDiagnostic.identifier(privateValue))
            assertEquals("REDACTED", TtsDiagnostic.locale(privateValue))
        }
        val exception = IllegalStateException("/data/private/transcript")
        assertEquals("IllegalStateException", TtsDiagnostic.errorType(exception))
        assertFalse(TtsFailure(TtsCode.TTS_ENGINE_ERROR).message!!.contains("private"))
    }
}
