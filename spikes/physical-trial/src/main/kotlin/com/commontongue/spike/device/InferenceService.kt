package com.commontongue.spike.device

import android.app.ActivityManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.SystemClock
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/** Private killable process: at most one serialized native inference job. */
class InferenceService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private var client: Messenger? = null
    private var asr = 0L
    private var t5 = 0L
    private val opus = mutableMapOf<String, Marian>()
    private var translator = "madlad"
    private var asrSize = "base"
    private var residentTurns = 0
    private var mode = DiagnosticMode.FULL_PIPELINE
    private var runId = ""
    private val startedWall = System.currentTimeMillis()
    private val main = Handler(Looper.getMainLooper())
    private val sampling =
        object : Runnable {
            override fun run() {
                reply("sample", Measurements.snapshot(this@InferenceService))
                main.postDelayed(this, 1000)
            }
        }
    private val messenger =
        Messenger(
            object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(message: Message) {
                    client = message.replyTo ?: client
                    val command = message.data.getString("command") ?: return
                    if (command == "terminate") {
                        reply(
                            "terminated",
                            JSONObject()
                                .put("method", "private_process_exit")
                                .put("memory_before", Measurements.snapshot(this@InferenceService)),
                        )
                        // Blocking tensor calls do not cooperate. OS exit frees all model
                        // mappings/jobs; next bind constructs a fresh worker and reloads.
                        Process.killProcess(Process.myPid())
                        return
                    }
                    if (!busy.compareAndSet(false, true)) {
                        reply(
                            "error",
                            JSONObject().put("stage", "UX").put("error", "Worker already busy"),
                        )
                        return
                    }
                    val payload = message.data.getString("payload") ?: "{}"
                    executor.execute {
                        var stage = "RESOURCE"
                        var transient: File? = null
                        var terminal: Pair<String, JSONObject>? = null
                        var request = JSONObject()
                        fun complete(event: String, data: JSONObject) {
                            terminal = event to data
                        }
                        fun checkpoint(
                            next: PipelineStage,
                            source: DiagnosticSource,
                            direction: String,
                            samples: Int? = null,
                        ) {
                            stage = next.name
                            val state =
                                StageCheckpoint(
                                    runId,
                                    request.getInt("turn_index"),
                                    mode,
                                    source,
                                    direction,
                                    next,
                                    samples,
                                    SystemClock.elapsedRealtime(),
                                )
                            StageJournal(File(filesDir, "diagnostics")).write("worker", state)
                            if (Build.VERSION.SDK_INT >= 30) {
                                // Advisory platform summary; the fsynced journal remains
                                // authoritative
                                // if Android throttles this optional API.
                                try {
                                    val bytes = state.encode().toByteArray(Charsets.US_ASCII)
                                    if (bytes.size <= 128)
                                        getSystemService(ActivityManager::class.java)
                                            .setProcessStateSummary(bytes)
                                } catch (_: Exception) {}
                            }
                            reply("pipeline_progress", JSONObject(state.exportFields()))
                        }
                        try {
                            request = JSONObject(payload)
                            when (command) {
                                "load" -> {
                                    check(asr == 0L && t5 == 0L && opus.isEmpty()) {
                                        "Unload before changing model configuration"
                                    }
                                    mode =
                                        DiagnosticMode.valueOf(
                                            request.optString("mode", "FULL_PIPELINE")
                                        )
                                    runId =
                                        request.getString("run_id").also {
                                            check(it.matches(Regex("[a-f0-9]{32}")))
                                        }
                                    reply(
                                        "worker_identity",
                                        JSONObject()
                                            .put("pid", Process.myPid())
                                            .put("started_wall_ms", startedWall),
                                    )
                                    translator =
                                        request.getString("translator").also {
                                            check(it in listOf("madlad", "opus"))
                                        }
                                    asrSize =
                                        request.getString("asr").also {
                                            check(it in listOf("base", "tiny"))
                                        }
                                    stage = "PACK_STATE"
                                    progress(
                                        stage,
                                        "Checking the installed test pack…",
                                        JSONObject()
                                            .put(
                                                "pack",
                                                ResearchFiles.diagnostics(
                                                    this@InferenceService,
                                                    translator,
                                                    asrSize,
                                                ),
                                            ),
                                    )
                                    val assetRoot = ResearchFiles.root(this@InferenceService)
                                    val paths = ModelPaths(assetRoot)
                                    val receipt = File(assetRoot, "provision.json")
                                    check(
                                        receipt.isFile &&
                                            JSONObject(receipt.readText())
                                                .getBoolean("verified_on_device")
                                    ) {
                                        "Install the Common Tongue test pack first."
                                    }
                                    check(
                                        paths.required(translator, asrSize).all {
                                            it.isFile && it.canRead()
                                        }
                                    ) {
                                        "Required private model files are missing or unreadable"
                                    }
                                    stage = "PACK_HASHES"
                                    progress(
                                        stage,
                                        "Checking installed files. The downloaded ZIP is not needed.",
                                    )
                                    if (ResearchFiles.hasPack(this@InferenceService))
                                        ResearchFiles.store(this@InferenceService).verifyInstalled()
                                    stage = "NATIVE_RUNTIME"
                                    progress(
                                        stage,
                                        "Opening the phone's local translation engine…",
                                        JSONObject()
                                            .put(
                                                "pack_hashes_verified",
                                                ResearchFiles.hasPack(this@InferenceService),
                                            ),
                                    )
                                    val nativeBuild = Native.buildInfo()
                                    check(
                                        Process.is64Bit() && nativeBuild.startsWith("arm64-v8a;")
                                    ) {
                                        "ARM64 runtime probe failed"
                                    }
                                    val before = Measurements.snapshot(this@InferenceService)
                                    residentTurns = 0
                                    val start = Measurements.now()
                                    if (mode.loadsTranslation) {
                                        stage = "TRANSLATION_MODEL"
                                        progress(
                                            stage,
                                            "Loading the translation model…",
                                            JSONObject()
                                                .put("native_runtime_verified", true)
                                                .put("native_build", nativeBuild),
                                        )
                                    }
                                    if (mode.loadsTranslation && translator == "madlad") {
                                        val loaded = JSONObject(Native.t5Load(paths.madlad.path))
                                        check(!loaded.has("error")) { loaded.optString("error") }
                                        check(loaded.optString("handle_kind") == "opaque-id-v1") {
                                            "Translation runtime handle contract mismatch"
                                        }
                                        t5 = OpaqueT5Handle.parse(loaded.get("handle"))
                                    } else if (mode.loadsTranslation) {
                                        for (direction in listOf("en-es", "es-en")) opus[
                                            direction] = Marian(paths.opus(direction))
                                    }
                                    val translationLoad =
                                        if (mode.loadsTranslation) Measurements.milliseconds(start)
                                        else 0.0
                                    val translationMemory =
                                        Measurements.snapshot(this@InferenceService)
                                    val asrStart = Measurements.now()
                                    if (mode.loadsAsr) {
                                        stage = "ASR_MODEL"
                                        progress(
                                            stage,
                                            "Loading speech recognition…",
                                            JSONObject()
                                                .put("native_runtime_verified", true)
                                                .put("native_build", nativeBuild)
                                                .put(
                                                    "translation_model_loaded",
                                                    mode.loadsTranslation,
                                                )
                                                .put("translation_load_ms", translationLoad),
                                        )
                                        asr = Native.asrCreate(paths.whisper(asrSize).path)
                                        check(asr != 0L) {
                                            "Speech-recognition model returned no handle"
                                        }
                                    }
                                    complete(
                                        "ready",
                                        JSONObject()
                                            .put("stage", "READY")
                                            .put("asr_model_loaded", mode.loadsAsr)
                                            .put("translation_model_loaded", mode.loadsTranslation)
                                            .put("native_runtime_verified", true)
                                            .put("mode", mode.name)
                                            .put("translator", translator)
                                            .put("asr", "Whisper $asrSize Q5_1")
                                            .put("translation_load_ms", translationLoad)
                                            .put(
                                                "asr_load_ms",
                                                if (mode.loadsAsr)
                                                    Measurements.milliseconds(asrStart)
                                                else 0.0,
                                            )
                                            .put("start_memory", before)
                                            .put("translation_loaded_memory", translationMemory)
                                            .put(
                                                "loaded_memory",
                                                Measurements.snapshot(this@InferenceService),
                                            )
                                            .put("native_build", nativeBuild)
                                            .put("threads", 4),
                                    )
                                }
                                "audio",
                                "text",
                                "asr_only" -> {
                                    check(request.getString("run_id") == runId)
                                    check(
                                        (command == "text" || asr != 0L) &&
                                            (command == "asr_only" || t5 != 0L || opus.isNotEmpty())
                                    ) {
                                        "Load models first"
                                    }
                                    val direction =
                                        request.getString("direction").also {
                                            check(it in listOf("en-es", "es-en"))
                                        }
                                    val start = Measurements.now()
                                    val cpuStart = Process.getElapsedCpuTime()
                                    var asrMs = 0.0
                                    var speechSeconds = 0.0
                                    var audioSource = DiagnosticSource.TEXT
                                    var sampleCount: Int? = null
                                    val source =
                                        if (command == "audio" || command == "asr_only") {
                                            val name = request.getString("file")
                                            val human =
                                                name.matches(Regex("capture-[a-f0-9]{32}\\.wav"))
                                            val fixture =
                                                name.matches(Regex("fixture-[a-z0-9-]+\\.wav"))
                                            check(human || fixture) { "Invalid local WAV filename" }
                                            audioSource =
                                                if (human) DiagnosticSource.HUMAN_MICROPHONE
                                                else DiagnosticSource.PRERECORDED_FIXTURE
                                            val wav =
                                                File(
                                                    if (human) filesDir
                                                    else ResearchFiles.root(this@InferenceService),
                                                    "audio/$name",
                                                )
                                            if (human) transient = wav
                                            checkpoint(
                                                PipelineStage.AUDIO_DECODE_START,
                                                audioSource,
                                                direction,
                                            )
                                            val pcm = readPcm(wav)
                                            sampleCount = pcm.size
                                            speechSeconds = pcm.size / 16000.0
                                            checkpoint(
                                                PipelineStage.AUDIO_DECODE_COMPLETE,
                                                audioSource,
                                                direction,
                                                sampleCount,
                                            )
                                            checkpoint(
                                                PipelineStage.ASR_START,
                                                audioSource,
                                                direction,
                                                sampleCount,
                                            )
                                            val begin = Measurements.now()
                                            Native.asrRun(asr, pcm, direction.take(2)).trim().also {
                                                asrMs = Measurements.milliseconds(begin)
                                                checkpoint(
                                                    PipelineStage.ASR_COMPLETE,
                                                    audioSource,
                                                    direction,
                                                    sampleCount,
                                                )
                                            }
                                        } else request.getString("text")
                                    check(source.isNotBlank()) { "No recognized/source text" }
                                    if (command == "asr_only") {
                                        complete(
                                            "asr_result",
                                            JSONObject()
                                                .put("recognized_text", source)
                                                .put("asr_ms", asrMs)
                                                .put("speech_seconds", speechSeconds)
                                                .put("mode", mode.name)
                                                .put("turn_index", request.getInt("turn_index")),
                                        )
                                    } else {
                                        checkpoint(
                                            PipelineStage.TRANSLATION_START,
                                            audioSource,
                                            direction,
                                            sampleCount,
                                        )
                                        val translationStart = Measurements.now()
                                        val output =
                                            if (translator == "madlad")
                                                JSONObject(
                                                    Native.t5Run(
                                                        t5,
                                                        JSONObject()
                                                            .put("text", source)
                                                            .put("direction", direction)
                                                            .toString(),
                                                        TranslationProgress { code ->
                                                            checkpoint(
                                                                TranslationBoundary.fromCode(code),
                                                                audioSource,
                                                                direction,
                                                                sampleCount,
                                                            )
                                                        },
                                                    )
                                                )
                                            else
                                                JSONObject()
                                                    .put(
                                                        "text",
                                                        opus.getValue(direction).translate(source),
                                                    )
                                                    .put("completed", true)
                                        check(!output.has("error")) { output.optString("error") }
                                        check(output.getBoolean("completed")) {
                                            "Translation capped at 128 tokens; partial output is not success"
                                        }
                                        check(output.getString("text").isNotBlank())
                                        checkpoint(
                                            PipelineStage.TRANSLATION_COMPLETE,
                                            audioSource,
                                            direction,
                                            sampleCount,
                                        )
                                        complete(
                                            "result",
                                            JSONObject()
                                                .put(
                                                    "case_id",
                                                    request.optString("case_id", "unscripted"),
                                                )
                                                .put("resident_turn_index", ++residentTurns)
                                                .put("first_after_model_load", residentTurns == 1)
                                                .put("direction", direction)
                                                .put(
                                                    "source_kind",
                                                    if (command == "text") "text"
                                                    else if (transient != null) "human_microphone"
                                                    else "prerecorded_fixture",
                                                )
                                                .put("recognized_text", source)
                                                .put("translation", output.getString("text"))
                                                .put("translator", translator)
                                                .put("mode", mode.name)
                                                .put("asr", asrSize)
                                                .put("asr_ms", asrMs)
                                                .put(
                                                    "translation_ms",
                                                    Measurements.milliseconds(translationStart),
                                                )
                                                .put("speech_seconds", speechSeconds)
                                                .put(
                                                    "worker_pipeline_ms",
                                                    Measurements.milliseconds(start),
                                                )
                                                .put(
                                                    "worker_cpu_ms",
                                                    Process.getElapsedCpuTime() - cpuStart,
                                                )
                                                .put(
                                                    "worker_memory",
                                                    Measurements.snapshot(this@InferenceService),
                                                )
                                                .put(
                                                    "offline",
                                                    Measurements.offline(this@InferenceService),
                                                ),
                                        )
                                    }
                                }
                                else -> error("Unknown command")
                            }
                        } catch (error: Throwable) {
                            complete(
                                "error",
                                JSONObject()
                                    .put("stage", stage)
                                    .put("operation", command)
                                    .put(
                                        "diagnostic_code",
                                        when (stage) {
                                            "PACK_STATE" -> "PACK_STATE_FAILED"
                                            "PACK_HASHES" -> "INSTALLED_FILE_CHECK_FAILED"
                                            "NATIVE_RUNTIME" ->
                                                if (error is LinkageError) "NATIVE_LINK_FAILED"
                                                else "NATIVE_PROBE_FAILED"
                                            "TRANSLATION_MODEL" -> "TRANSLATION_LOAD_FAILED"
                                            "ASR_MODEL" -> "ASR_LOAD_FAILED"
                                            else -> "PIPELINE_FAILED"
                                        },
                                    )
                                    .put("error_type", error.javaClass.simpleName)
                                    .put("error", error.message ?: error.javaClass.simpleName)
                                    .apply {
                                        NativeLinkDiagnostic.fromError(error).forEach { (key, value)
                                            ->
                                            put(key, value)
                                        }
                                    },
                            )
                        } finally {
                            transient?.delete()
                            busy.set(false)
                        }
                        // The UI may immediately send a fixture/turn after Ready. Release
                        // the job slot and delete source audio before delivering completion.
                        terminal?.let { (event, data) -> reply(event, data) }
                    }
                }
            }
        )

    private fun progress(stage: String, message: String, details: JSONObject = JSONObject()) {
        reply("load_progress", details.put("stage", stage).put("message", message))
    }

    private fun reply(event: String, payload: JSONObject) {
        try {
            client?.send(
                Message.obtain().apply {
                    data =
                        Bundle().apply {
                            putString("event", event)
                            putString("payload", payload.toString())
                        }
                }
            )
        } catch (_: Exception) {
            Process.killProcess(Process.myPid())
        }
    }

    override fun onBind(intent: Intent): IBinder {
        main.post(sampling)
        return messenger.binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        // Losing the only UI owner always abandons the entire research worker.
        Process.killProcess(Process.myPid())
        return false
    }

    override fun onDestroy() {
        main.removeCallbacks(sampling)
        executor.shutdownNow()
        super.onDestroy()
    }
}
