package com.commontongue.spike.device

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal enum class DiagnosticMode(val loadsAsr: Boolean, val loadsTranslation: Boolean) {
    WHISPER_ONLY(true, false),
    MADLAD_ONLY(false, true),
    FULL_PIPELINE(true, true),
}

internal enum class PipelineStage {
    AUDIO_CAPTURE_COMPLETE,
    AUDIO_DECODE_START,
    AUDIO_DECODE_COMPLETE,
    ASR_START,
    ASR_COMPLETE,
    TRANSLATION_START,
    TOKENIZE_START,
    TOKENIZE_COMPLETE,
    ENCODER_START,
    ENCODER_COMPLETE,
    DECODER_START,
    FIRST_TOKEN,
    TRANSLATION_COMPLETE,
    TTS_START,
    TTS_COMPLETE,
}

internal enum class DiagnosticSource {
    HUMAN_MICROPHONE,
    PRERECORDED_FIXTURE,
    TEXT,
}

/** Only finite metadata. runId correlates private journals and is never exported. */
internal data class StageCheckpoint(
    val runId: String,
    val turn: Int,
    val mode: DiagnosticMode,
    val source: DiagnosticSource,
    val direction: String,
    val stage: PipelineStage,
    val sampleCount: Int?,
    val elapsedMs: Long,
) {
    init {
        require(runId.matches(Regex("[a-f0-9]{32}")))
        require(turn in 1..1000000 && direction in setOf("en-es", "es-en"))
        require(sampleCount == null || sampleCount in 0..496000)
        require(elapsedMs >= 0)
    }

    fun encode(): String =
        listOf(
                "CT5V5",
                runId,
                turn,
                mode.name,
                source.name,
                direction,
                stage.name,
                sampleCount ?: -1,
                elapsedMs,
            )
            .joinToString("|")

    fun exportFields(): Map<String, Any> = buildMap {
        put("stage", stage.name)
        put("mode", mode.name)
        put("source_kind", source.name.lowercase())
        put("direction", direction)
        put("turn_index", turn)
        put("elapsed_ms", elapsedMs)
        sampleCount?.let {
            put("sample_count", it)
            put("sample_rate_hz", 16000)
            put("channels", 1)
        }
    }

    companion object {
        fun parse(encoded: String): StageCheckpoint? =
            try {
                val parts = encoded.split('|')
                require(parts.size == 9 && parts[0] == "CT5V5" && encoded.length <= 160)
                StageCheckpoint(
                    parts[1],
                    parts[2].toInt(),
                    DiagnosticMode.valueOf(parts[3]),
                    DiagnosticSource.valueOf(parts[4]),
                    parts[5],
                    PipelineStage.valueOf(parts[6]),
                    parts[7].toInt().let { if (it == -1) null else it },
                    parts[8].toLong(),
                )
            } catch (_: Exception) {
                null
            }
    }
}

/** Flush a whole checkpoint before crossing a native boundary, then atomically replace it. */
internal class StageJournal(private val directory: File) {
    fun write(owner: String, checkpoint: StageCheckpoint) {
        require(owner in setOf("ui", "worker"))
        directory.mkdirs()
        val temporary = File(directory, "$owner-stage.tmp")
        FileOutputStream(temporary).use {
            it.write(checkpoint.encode().toByteArray(Charsets.US_ASCII))
            it.fd.sync()
        }
        Files.move(
            temporary.toPath(),
            File(directory, "$owner-stage.dat").toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    fun latest(runId: String, turn: Int? = null): StageCheckpoint? =
        listOf("ui", "worker")
            .mapNotNull { owner ->
                try {
                    StageCheckpoint.parse(File(directory, "$owner-stage.dat").readText())
                } catch (_: Exception) {
                    null
                }
            }
            .filter { it.runId == runId && (turn == null || it.turn == turn) }
            .maxWithOrNull(compareBy<StageCheckpoint> { it.turn }.thenBy { it.elapsedMs })
}

/** Reject records for the UI, an older worker, or a reused PID before attributing an exit. */
internal object ExitMatch {
    fun belongs(
        recordPid: Int,
        workerPid: Int,
        process: String,
        expectedProcess: String,
        timestamp: Long,
        started: Long,
        now: Long,
    ): Boolean =
        workerPid > 0 &&
            recordPid == workerPid &&
            process == expectedProcess &&
            timestamp in started..now
}
