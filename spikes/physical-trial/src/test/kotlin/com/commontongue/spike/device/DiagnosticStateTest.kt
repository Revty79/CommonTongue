package com.commontongue.spike.device

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class DiagnosticStateTest {
    private val run = "a".repeat(32)

    private fun state(turn: Int, stage: PipelineStage, elapsed: Long, id: String = run) =
        StageCheckpoint(
            id,
            turn,
            DiagnosticMode.FULL_PIPELINE,
            DiagnosticSource.HUMAN_MICROPHONE,
            "en-es",
            stage,
            31680,
            elapsed,
        )

    @Test
    fun flushedNativeBoundarySurvivesJournalRecreationAndIgnoresPartialTemporaryFile() {
        val directory = Files.createTempDirectory("stage-test").toFile()
        try {
            StageJournal(directory).write("worker", state(1, PipelineStage.ASR_START, 2))
            java.io.File(directory, "worker-stage.tmp").writeText("partial")
            assertEquals(PipelineStage.ASR_START, StageJournal(directory).latest(run)?.stage)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun lastBoundaryUsesCurrentRunAndTurnRatherThanStaleModelSession() {
        val directory = Files.createTempDirectory("stage-test").toFile()
        try {
            val journal = StageJournal(directory)
            journal.write("worker", state(1, PipelineStage.ASR_COMPLETE, 10))
            journal.write("ui", state(2, PipelineStage.AUDIO_CAPTURE_COMPLETE, 11))
            assertEquals(PipelineStage.AUDIO_CAPTURE_COMPLETE, journal.latest(run)?.stage)
            assertEquals(PipelineStage.ASR_COMPLETE, journal.latest(run, 1)?.stage)
            assertNull(journal.latest("b".repeat(32)))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun corruptedUnknownOrPrivateCheckpointDataCannotBeExported() {
        val encoded = state(1, PipelineStage.ASR_START, 1).encode()
        assertEquals(state(1, PipelineStage.ASR_START, 1), StageCheckpoint.parse(encoded))
        for (invalid in
            listOf(
                encoded + "|private",
                encoded.replace("ASR_START", "/private/path"),
                encoded.replace("31680", "-2"),
                encoded.replace("31680", "999999999"),
                "private account",
            )) {
            assertNull(StageCheckpoint.parse(invalid))
        }
        val exported = state(1, PipelineStage.ASR_START, 1).exportFields()
        assertFalse(exported.containsKey("run_id"))
        assertFalse(exported.containsValue(run))
    }

    @Test
    fun componentModesRequireOnlyTheirOwnModel() {
        assertTrue(DiagnosticMode.WHISPER_ONLY.loadsAsr)
        assertFalse(DiagnosticMode.WHISPER_ONLY.loadsTranslation)
        assertTrue(DiagnosticMode.MADLAD_ONLY.loadsTranslation)
        assertFalse(DiagnosticMode.MADLAD_ONLY.loadsAsr)
        assertTrue(
            DiagnosticMode.FULL_PIPELINE.loadsAsr && DiagnosticMode.FULL_PIPELINE.loadsTranslation
        )
    }

    @Test
    fun historicalExitCannotBeAssignedToAnotherPidUiProcessOrOlderWorker() {
        assertTrue(
            ExitMatch.belongs(42, 42, "research:inference", "research:inference", 110, 100, 120)
        )
        assertFalse(
            ExitMatch.belongs(41, 42, "research:inference", "research:inference", 110, 100, 120)
        )
        assertFalse(ExitMatch.belongs(42, 42, "research", "research:inference", 110, 100, 120))
        assertFalse(
            ExitMatch.belongs(42, 42, "research:inference", "research:inference", 99, 100, 120)
        )
        assertFalse(
            ExitMatch.belongs(42, 42, "research:inference", "research:inference", 121, 100, 120)
        )
        assertFalse(
            ExitMatch.belongs(0, 0, "research:inference", "research:inference", 110, 100, 120)
        )
    }
}
