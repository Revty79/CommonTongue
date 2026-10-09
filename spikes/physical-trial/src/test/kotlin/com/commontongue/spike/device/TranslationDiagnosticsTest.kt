package com.commontongue.spike.device

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class TranslationDiagnosticsTest {
    @Test
    fun taggedPointerCannotSurviveOldAndroidJsonNumberTransport() {
        // Synthetic address, no actual device address is read or exported.
        val original = "12970367451118458488" // 0xb400007a12345678
        assertThrows(NumberFormatException::class.java) { original.toLong() }
        val lossy = original.toDouble().toLong()
        assertEquals(Long.MAX_VALUE, lossy)
        assertThrows(IllegalArgumentException::class.java) { OpaqueT5Handle.parse(lossy) }
        assertThrows(IllegalArgumentException::class.java) {
            OpaqueT5Handle.parse(original.toDouble())
        }
    }

    @Test
    fun opaqueHandlesAreExactAndWrongOrStaleRepresentationsFailClosed() {
        assertEquals(1L, OpaqueT5Handle.parse(1))
        assertEquals(0xffffffffL, OpaqueT5Handle.parse(0xffffffffL))
        for (invalid in listOf(null, 0, -1L, Long.MAX_VALUE, 0x100000000L, 1.0, "1")) {
            assertThrows(IllegalArgumentException::class.java) { OpaqueT5Handle.parse(invalid) }
        }
    }

    @Test
    fun everyTranslationBoundaryIsDurableAndContainsNoTokensOrModelAddresses() {
        val directory = Files.createTempDirectory("translation-stage-test").toFile()
        try {
            val stages =
                listOf(
                    PipelineStage.TOKENIZE_START,
                    PipelineStage.TOKENIZE_COMPLETE,
                    PipelineStage.ENCODER_START,
                    PipelineStage.ENCODER_COMPLETE,
                    PipelineStage.DECODER_START,
                    PipelineStage.FIRST_TOKEN,
                )
            stages.forEachIndexed { index, stage ->
                assertEquals(stage, TranslationBoundary.fromCode(index + 1))
                val state =
                    StageCheckpoint(
                        "a".repeat(32),
                        1,
                        DiagnosticMode.MADLAD_ONLY,
                        DiagnosticSource.TEXT,
                        "en-es",
                        stage,
                        null,
                        index.toLong(),
                    )
                StageJournal(directory).write("worker", state)
                assertEquals(stage, StageJournal(directory).latest(state.runId)?.stage)
                assertFalse(state.exportFields().containsKey("handle"))
                assertFalse(state.exportFields().containsKey("token"))
            }
            assertThrows(IllegalStateException::class.java) { TranslationBoundary.fromCode(7) }
        } finally {
            directory.deleteRecursively()
        }
    }
}
