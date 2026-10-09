package com.commontongue.spike.device

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.util.AtomicFile
import android.view.MotionEvent
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

class TrialActivity : Activity() {
    private lateinit var stateTitle: TextView
    private lateinit var status: TextView
    private lateinit var radioStatus: TextView
    private lateinit var diagnosticStatus: TextView
    private lateinit var output: TextView
    private lateinit var timing: TextView
    private lateinit var model: Spinner
    private lateinit var asr: Spinner
    private lateinit var speech: OfflineSpeech
    private lateinit var installPack: Button
    private lateinit var startTesting: Button
    private lateinit var voicesButton: Button
    private lateinit var fixtureButton: Button
    private lateinit var replayButton: Button
    private lateinit var speechCheckButton: Button
    private var speechCheckRunning = false
    private val diagnosticButtons = mutableListOf<Pair<Button, DiagnosticMode>>()
    private var activeMode = DiagnosticMode.FULL_PIPELINE
    private var diagnosticRunning = false
    private var switchingWorker = false
    private var runId = ""
    private var turnIndex = 0
    private var workerPid = 0 // Private correlation only; never metric/export data.
    private var workerStartedWall = 0L
    private val exitExecutor = Executors.newSingleThreadExecutor()
    private val languageButtons = mutableListOf<Pair<Button, String>>()
    private val session = ResearchSession()
    private var packRecovered = false
    private var permissionLoadPending = false
    private var loadReport = JSONObject()
    private var loadFailure: String? = null
    private val importExecutor = Executors.newSingleThreadExecutor()
    private val importCancelled = AtomicBoolean(false)
    private var importing = false
    private var selectingPack = false
    private var destroyed = false
    private var settingUpVoices = false
    private val main = Handler(Looper.getMainLooper())
    private var worker: Messenger? = null
    private var replies: Messenger? = null
    private var connection: ServiceConnection? = null
    private var generation = 0
    private val ready: Boolean
        get() = session.modelsReady

    private val processing: Boolean
        get() = session.busy

    private var foreground = false
    private var capture: Capture? = null
    private var captureDirection = "en-es"
    private var released: Long? = null
    private var latest: JSONObject? = null
    private var latestDirection = "en-es"
    private var fixturePlan: JSONArray? = null
    private var fixtureIndex = 0
    private var fixtureResults = JSONArray()
    private val activityStart = Measurements.now()
    private val samples =
        object : Runnable {
            override fun run() {
                if (!foreground) return
                metric("ui_sample", Measurements.snapshot(this@TrialActivity))
                updateRadioStatus()
                main.postDelayed(this, 1000)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val layout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 20, 24, 24)
            }
        fun label(text: String): TextView =
            TextView(this).apply {
                this.text = text
                textSize = 16f
                layout.addView(this)
            }
        fun button(text: String, action: () -> Unit): Button =
            Button(this).apply {
                this.text = text
                setOnClickListener { safely(action) }
                layout.addView(this)
            }
        label("Common Tongue — physical research trial")
        label(
            "Human audio is transient. Text stays in memory until you explicitly save a result. Review exports for personal details."
        )
        stateTitle = label("Checking installed test pack…")
        stateTitle.textSize = 22f
        stateTitle.setTypeface(stateTitle.typeface, android.graphics.Typeface.BOLD)
        status =
            label(
                "Tap Install Test Pack and choose CommonTongue-Pass5-Test-Pack.zip from Downloads."
            )
        installPack = button("Install Test Pack") { chooseTestPack() }
        voicesButton =
            button("Set Up Offline Voices") {
                settingUpVoices = true
                speech.openVoiceSetup(this)
            }
        model =
            Spinner(this).apply {
                adapter =
                    ArrayAdapter(
                        this@TrialActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        listOf("madlad", "opus"),
                    )
                layout.addView(this)
            }
        asr =
            Spinner(this).apply {
                adapter =
                    ArrayAdapter(
                        this@TrialActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        listOf("base", "tiny"),
                    )
                layout.addView(this)
            }
        startTesting =
            button("Start Testing") {
                if (ready && activeMode != DiagnosticMode.FULL_PIPELINE)
                    startDiagnostic(DiagnosticMode.FULL_PIPELINE, diagnostic = false)
                else bindModels()
            }
        startTesting.isEnabled = false
        holdButton(layout, "English — HOLD TO SPEAK", "en-es")
        holdButton(layout, "Español — MANTENER PARA HABLAR", "es-en")
        button("Cancel / unload models") {
            importCancelled.set(true)
            cancel("user_cancel", preserveStatus = importing)
        }
        output = label("Recognized text and translation appear here.")
        replayButton =
            button("Replay latest translation") {
                val result = latest ?: error("No translation")
                session.beginTurn()
                updateControls()
                replay(JSONObject(result.toString()), null, recordTurn = false)
            }
        timing = label("Timing and model memory appear here.")
        speechCheckButton =
            button("Check offline speech (English + Spanish)") { checkOfflineSpeech() }
        label(
            "Speech check: listen for four short phrases. No microphone or translation models are used. Export Research Results afterward."
        )
        fixtureButton = button("Run fixed prerecorded WAV/text checks") { startFixtures() }
        label("Crash isolation: run each check once, in order. No microphone is used.")
        diagnosticButtons +=
            button("1. Check speech recognition only") {
                startDiagnostic(DiagnosticMode.WHISPER_ONLY)
            } to DiagnosticMode.WHISPER_ONLY
        diagnosticButtons +=
            button("2. Check translation only") { startDiagnostic(DiagnosticMode.MADLAD_ONLY) } to
                DiagnosticMode.MADLAD_ONLY
        diagnosticButtons +=
            button("3. Check the full recorded-sample pipeline") {
                startDiagnostic(DiagnosticMode.FULL_PIPELINE)
            } to DiagnosticMode.FULL_PIPELINE
        radioStatus =
            label(
                "Local translation works with radios on or off. Airplane mode is only for the offline-proof test."
            )
        diagnosticStatus = label("Models have not been loaded in this session.")
        button("Export Research Results") {
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "CommonTongue-Pass5-Research-Results.json")
                },
                82,
            )
        }
        button("Save this test's text and metrics locally") {
            val result = latest ?: error("No completed result")
            val directory = File(filesDir, "results").apply { mkdirs() }
            File(directory, "saved-${UUID.randomUUID().toString().replace("-", "")}.json")
                .writeText(result.toString(2))
            status.text =
                "Saved in app-private research storage. Export requires explicit text/privacy review. Audio was deleted."
        }
        button("Clear locally saved test text") {
            File(filesDir, "results")
                .listFiles()
                ?.filter { it.name.startsWith("saved-") }
                ?.forEach { it.delete() }
            latest = null
            output.text = "Saved test text cleared."
            status.text = "Local metrics and authored fixture results remain."
        }
        setContentView(ScrollView(this).apply { addView(layout) })
        try {
            loadReport = JSONObject(File(filesDir, "load-diagnostics.json").readText())
        } catch (_: Exception) {}
        updateDiagnosticStatus()
        cleanTransient()
        ResearchExport.saveInventory(this)
        speech = createSpeech()
        importExecutor.execute {
            var recoveryError: String? = null
            try {
                ResearchFiles.store(this).recover()
            } catch (_: Exception) {
                recoveryError =
                    "The installed test pack could not be checked. Export Research Results so we can inspect it."
            }
            main.post {
                if (!destroyed) {
                    packRecovered = true
                    metric(
                        "pack_state_after_restart",
                        ResearchFiles.diagnostics(this, "madlad", "base"),
                    )
                    if (!importing) refreshReadiness()
                    recoveryError?.let { status.text = it }
                }
            }
        }
        metric(
            "activity_created",
            JSONObject().put("launch_to_ui_ms", Measurements.milliseconds(activityStart)),
        )
    }

    private fun refreshReadiness() {
        updateControls()
        if (
            importing ||
                selectingPack ||
                processing ||
                switchingWorker ||
                !::speech.isInitialized ||
                !packRecovered ||
                speechCheckRunning
        )
            return
        val provisioned = packPresent()
        status.text =
            when {
                !provisioned ->
                    "Tap Install Test Pack and select CommonTongue-Pass5-Test-Pack.zip from Downloads."
                !speech.languagesReady() -> speech.readinessMessage()
                session.phase == ResearchSession.Phase.LOAD_FAILED ->
                    loadFailure ?: "Models could not be loaded. Export Research Results."
                ready && activeMode != DiagnosticMode.FULL_PIPELINE ->
                    "Component check ready. Choose the next check, or Start Testing to load both models."
                ready -> "Ready to Speak. Hold either language button."
                else -> "Pack Installed. Tap Start Testing to load models."
            }
        continuePermissionLoad()
    }

    private fun packPresent(): Boolean =
        packRecovered && (ResearchFiles.hasPack(this) || ResearchFiles.hasLegacyPack(this))

    private fun updateControls() {
        if (!::startTesting.isInitialized || !::speech.isInitialized) return
        val installed = packPresent()
        val available =
            foreground &&
                !importing &&
                !selectingPack &&
                !switchingWorker &&
                !speechCheckRunning &&
                installed &&
                speech.languagesReady()
        val speak =
            available &&
                session.canSpeak &&
                fixturePlan == null &&
                activeMode == DiagnosticMode.FULL_PIPELINE &&
                !diagnosticRunning
        languageButtons.forEach { (button, direction) ->
            // Keep the held button enabled until ACTION_UP so recording can end normally.
            button.isEnabled =
                speak || (available && session.recording && direction == captureDirection)
        }
        startTesting.isEnabled =
            available &&
                (!ready || activeMode != DiagnosticMode.FULL_PIPELINE) &&
                !processing &&
                !session.recording
        diagnosticButtons.forEach { (button, mode) ->
            button.isEnabled =
                foreground &&
                    installed &&
                    !switchingWorker &&
                    !speechCheckRunning &&
                    !importing &&
                    !selectingPack &&
                    !processing &&
                    !session.recording &&
                    (mode != DiagnosticMode.FULL_PIPELINE || speech.languagesReady())
        }
        fixtureButton.isEnabled = speak
        replayButton.isEnabled = speak && latest != null
        installPack.isEnabled =
            foreground &&
                packRecovered &&
                !importing &&
                !processing &&
                !session.recording &&
                !speechCheckRunning
        voicesButton.isEnabled =
            foreground && !importing && !processing && !session.recording && !speechCheckRunning
        speechCheckButton.isEnabled =
            foreground &&
                speech.engineInitialized() &&
                !importing &&
                !selectingPack &&
                !processing &&
                !session.recording &&
                !switchingWorker &&
                !speechCheckRunning
        model.isEnabled =
            !ready &&
                !processing &&
                !importing &&
                !session.recording &&
                !switchingWorker &&
                !speechCheckRunning
        asr.isEnabled = model.isEnabled
        stateTitle.text =
            when {
                speechCheckRunning -> "Checking Offline Speech"
                importing -> "Installing Test Pack"
                !packRecovered -> "Checking installed test pack…"
                !installed -> "Test Pack Not Installed"
                session.phase == ResearchSession.Phase.LOADING_MODELS -> "Loading Models"
                ready && diagnosticRunning -> "Component Check Running"
                ready && activeMode != DiagnosticMode.FULL_PIPELINE -> "Component Check Ready"
                ready && speech.languagesReady() -> "Ready to Speak"
                ready -> "Models Loaded — Prepare Offline Voices"
                session.phase == ResearchSession.Phase.LOAD_FAILED ->
                    "Pack Installed — Models Unavailable"
                else -> "Pack Installed"
            }
    }

    private fun updateRadioStatus() {
        if (!::radioStatus.isInitialized) return
        radioStatus.text =
            if (Measurements.offlineProofReady(this))
                "Offline-proof condition active: Airplane Mode on; Wi-Fi and mobile data off."
            else
                "Local translation available. Radios are on or unconfirmed; the offline-proof condition is not active."
    }

    private fun recordLoad(payload: JSONObject, state: String) {
        loadReport.put("state", state)
        for (key in
            listOf(
                "stage",
                "pack",
                "pack_hashes_verified",
                "native_runtime_verified",
                "native_build",
                "translation_model_loaded",
                "asr_model_loaded",
                "translation_load_ms",
                "asr_load_ms",
                "diagnostic_code",
                "error_type",
                "mode",
                "native_missing_library",
                "native_missing_symbol",
            )) {
            if (payload.has(key)) loadReport.put(key, payload.get(key))
        }
        // Only allowlisted model-loading observations; never raw error/source text or paths.
        try {
            val temporary = File(filesDir, "load-diagnostics.tmp")
            FileOutputStream(temporary).use {
                it.write(loadReport.toString(2).toByteArray(Charsets.UTF_8))
                it.fd.sync()
            }
            temporary.renameTo(File(filesDir, "load-diagnostics.json"))
        } catch (_: Exception) {}
        updateDiagnosticStatus()
    }

    private fun updateDiagnosticStatus() {
        fun checked(key: String) = if (loadReport.optBoolean(key)) "complete" else "not completed"
        diagnosticStatus.text =
            "Last model load: ${loadReport.optString("state", "not attempted")}\n" +
                "Installed-file check: ${checked("pack_hashes_verified")}\n" +
                "Local engine: ${checked("native_runtime_verified")}\n" +
                "Translation model: ${checked("translation_model_loaded")}\n" +
                "Speech recognition: ${checked("asr_model_loaded")}"
    }

    private fun chooseTestPack() {
        check(!importing) { "Please wait for the test pack installation." }
        cancel("choose_test_pack", preserveStatus = true)
        selectingPack = true
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            81,
        )
    }

    @Deprecated("Research uses the framework file picker without another dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 82) {
            if (resultCode == RESULT_OK && data?.data != null)
                safely {
                    contentResolver.openOutputStream(data.data!!)!!.use {
                        it.write(
                            ResearchExport.results(this).toString(2).toByteArray(Charsets.UTF_8)
                        )
                    }
                    status.text =
                        "Research results exported. No human recordings or saved human test text included."
                }
            return
        }
        if (requestCode != 81) return
        selectingPack = false
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) {
            refreshReadiness()
            return
        }
        importing = true
        importCancelled.set(false)
        updateControls()
        status.text = "Installing test pack… 0%. Keep this app open."
        importExecutor.execute {
            var lastProgress = -1
            try {
                val stream =
                    contentResolver.openInputStream(uri)
                        ?: throw PackProblem("This test pack is incomplete.")
                stream.use {
                    ResearchFiles.store(this).install(
                        it,
                        { filesDir.usableSpace },
                        { importCancelled.get() },
                    ) { percent ->
                        if (percent != lastProgress) {
                            lastProgress = percent
                            main.post {
                                if (!destroyed)
                                    status.text =
                                        "Installing and checking test pack… $percent%. Keep this app open."
                            }
                        }
                    }
                }
                main.post {
                    if (!destroyed) {
                        importing = false
                        installPack.isEnabled = true
                        refreshReadiness()
                        status.text = "Installation complete. " + status.text
                    }
                }
            } catch (error: Exception) {
                val message =
                    if (error is PackProblem) error.message
                    else "Installation was interrupted. Tap Install Test Pack to try again."
                main.post {
                    if (!destroyed) {
                        importing = false
                        installPack.isEnabled = true
                        refreshReadiness()
                        status.text = message
                    }
                }
            }
        }
    }

    private fun safely(action: () -> Unit) {
        try {
            action()
        } catch (error: Exception) {
            status.text = "Error: ${error.message}"
            updateControls()
        }
    }

    private fun holdButton(layout: LinearLayout, text: String, direction: String) {
        layout.addView(
            Button(this).apply {
                this.text = text
                isEnabled = false
                languageButtons.add(this to direction)
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> safely { startCapture(direction) }
                        MotionEvent.ACTION_UP -> {
                            view.performClick()
                            safely { stopCapture(true) }
                        }
                        MotionEvent.ACTION_CANCEL -> safely { stopCapture(false) }
                    }
                    true
                }
            }
        )
    }

    private fun startCapture(direction: String) {
        check(session.canSpeak && capture == null) {
            "Wait for Ready to Speak before holding a language button."
        }
        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 5)
            status.text = "Allow microphone access, then hold the language button again."
            return
        }
        speech.cancel()
        captureDirection = direction
        turnIndex++
        session.beginRecording()
        try {
            capture = Capture().also { it.start() }
        } catch (error: Exception) {
            session.endRecording(false)
            updateControls()
            throw error
        }
        updateControls()
        status.text = "Recording only while held (maximum 30 seconds). Release to translate."
    }

    private fun stopCapture(submit: Boolean) {
        val active = capture ?: return
        capture = null
        if (!submit) {
            session.endRecording(false)
            active.stop(null)
            status.text = "Recording discarded"
            updateControls()
            return
        }
        released = Measurements.now() // Button release, before AudioRecord stop/join/WAV/IPC.
        session.endRecording(true)
        updateControls()
        val directory = File(filesDir, "audio").apply { mkdirs() }
        val audio = File(directory, "capture-${UUID.randomUUID().toString().replace("-", "")}.wav")
        try {
            val duration = active.stop(audio)
            traceUi(
                PipelineStage.AUDIO_CAPTURE_COMPLETE,
                DiagnosticSource.HUMAN_MICROPHONE,
                captureDirection,
                ((audio.length() - 44) / 2).toInt(),
            )
            status.text = "Recognizing → translating → offline speech"
            latestDirection = captureDirection
            send("audio", JSONObject().put("direction", captureDirection).put("file", audio.name))
            metric("capture_release", JSONObject().put("speech_seconds", duration))
        } catch (error: Exception) {
            audio.delete()
            session.finishTurn()
            updateControls()
            throw error
        }
    }

    private fun bindModels(
        requestedMode: DiagnosticMode = DiagnosticMode.FULL_PIPELINE,
        diagnostic: Boolean = false,
    ) {
        check(!importing && !selectingPack) { "Finish installing the test pack first." }
        check(packPresent()) { "Install the test pack before loading models." }
        if (requestedMode == DiagnosticMode.FULL_PIPELINE)
            check(speech.languagesReady()) { "Prepare offline English and Spanish voices first." }
        check(connection == null) { "Cancel/unload before changing or reloading models" }
        // A permission dialog can pause the Activity and unload its worker. Obtain
        // microphone permission before constructing the expensive model process.
        if (
            !diagnostic &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                    PackageManager.PERMISSION_GRANTED
        ) {
            permissionLoadPending = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 5)
            status.text = "Allow microphone access to start testing."
            return
        }
        session.beginLoad()
        activeMode = requestedMode
        diagnosticRunning = diagnostic
        runId = UUID.randomUUID().toString().replace("-", "")
        turnIndex = 0
        workerPid = 0
        workerStartedWall = 0L
        File(filesDir, "pipeline-diagnostics.json").writeText("{}")
        loadFailure = null
        loadReport = JSONObject()
        recordLoad(
            JSONObject()
                .put("stage", "WORKER_BIND")
                .put(
                    "pack",
                    ResearchFiles.diagnostics(
                        this,
                        model.selectedItem.toString(),
                        asr.selectedItem.toString(),
                    ),
                ),
            "LOADING",
        )
        status.text = "Loading Models. Opening the local model worker…"
        updateControls()
        val epoch = ++generation
        model.isEnabled = false
        asr.isEnabled = false
        replies =
            Messenger(
                object : Handler(Looper.getMainLooper()) {
                    override fun handleMessage(message: Message) {
                        if (epoch != generation || !foreground) return
                        val event = message.data.getString("event") ?: return
                        val payload = JSONObject(message.data.getString("payload") ?: "{}")
                        when (event) {
                            "worker_identity" -> {
                                workerPid = payload.getInt("pid")
                                workerStartedWall = payload.getLong("started_wall_ms")
                            }
                            "pipeline_progress" -> recordPipeline(payload)
                            "sample" -> metric("worker_sample", payload)
                            "load_progress" -> {
                                recordLoad(payload, "LOADING")
                                status.text = "Loading Models. ${payload.getString("message")}"
                                metric("model_load_stage", payload)
                            }
                            "ready" -> {
                                session.modelsLoaded()
                                recordLoad(payload, "READY")
                                status.text = "Ready to Speak. Hold either language button."
                                updateControls()
                                timing.text = payload.toString(2)
                                metric("models_loaded", payload)
                                if (diagnosticRunning) {
                                    safely { runComponentCase() }
                                    return
                                }
                                if (intent.getBooleanExtra("fixtures", false)) {
                                    intent.removeExtra("fixtures")
                                    safely { startFixtures() }
                                }
                            }
                            "result" -> {
                                latest = payload
                                output.text =
                                    "Recognized:\n${payload.getString("recognized_text")}\n\nTranslated:\n${payload.getString("translation")}"
                                if (diagnosticRunning && activeMode == DiagnosticMode.MADLAD_ONLY) {
                                    finishDiagnostic(
                                        JSONObject()
                                            .put(
                                                "translation_ms",
                                                payload.getDouble("translation_ms"),
                                            )
                                    )
                                } else replay(payload, released)
                            }
                            "asr_result" -> {
                                output.text =
                                    "Recorded sample recognized:\n${payload.getString("recognized_text")}"
                                finishDiagnostic(
                                    JSONObject().put("asr_ms", payload.getDouble("asr_ms"))
                                )
                            }
                            "error" -> {
                                if (diagnosticRunning) recordDiagnosticResult("FAILED")
                                metric("error", payload)
                                loadFailure = failureMessage(payload.optString("stage"))
                                recordLoad(
                                    payload,
                                    if (
                                        payload.optString("operation") == "load" ||
                                            session.phase == ResearchSession.Phase.LOADING_MODELS
                                    )
                                        "FAILED"
                                    else "TURN_FAILED",
                                )
                                cancel("inference_error", preserveStatus = true)
                                session.failLoad()
                                status.text = loadFailure
                                updateControls()
                            }
                            "terminated" -> metric("worker_terminated", payload)
                        }
                    }
                }
            )
        connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    if (epoch != generation) return
                    worker = Messenger(service)
                    try {
                        send(
                            "load",
                            JSONObject()
                                .put("translator", model.selectedItem.toString())
                                .put("asr", asr.selectedItem.toString()),
                        )
                    } catch (_: Exception) {
                        onServiceDisconnected(name)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    if (epoch != generation) return
                    val exitPid = workerPid
                    val aliveAtDisconnect = WorkerExitDiagnostics.isAlive(exitPid)
                    val exitStarted = workerStartedWall
                    val exitRun = runId
                    val exitMode = activeMode
                    val lastCheckpoint = StageJournal(File(filesDir, "diagnostics")).latest(runId)
                    lastCheckpoint?.let { recordPipeline(JSONObject(it.exportFields())) }
                    if (diagnosticRunning) recordDiagnosticResult("WORKER_EXIT")
                    recordLoad(
                        JSONObject().put("diagnostic_code", "WORKER_EXIT_OR_DISCONNECT"),
                        "WORKER_STOPPED",
                    )
                    loadFailure =
                        "The phone stopped the model worker. Export Research Results; please avoid repeatedly retrying this load."
                    metric(
                        "worker_disconnected",
                        JSONObject()
                            .put("reason", "unexpected_process_exit_or_disconnect")
                            .put("last_load_stage", loadReport.optString("stage"))
                            .put(
                                "last_pipeline_stage",
                                lastCheckpoint?.stage?.name ?: "NOT_STARTED",
                            ),
                    )
                    cancel("worker_disconnect", preserveStatus = true)
                    session.failLoad()
                    status.text = loadFailure
                    updateControls()
                    collectWorkerExit(
                        exitPid,
                        exitStarted,
                        exitRun,
                        exitMode,
                        lastCheckpoint?.stage?.name ?: "NOT_STARTED",
                        aliveAtDisconnect,
                    )
                }

                override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)

                override fun onNullBinding(name: ComponentName) = onServiceDisconnected(name)
            }
        try {
            check(
                bindService(
                    Intent(this, InferenceService::class.java),
                    connection!!,
                    BIND_AUTO_CREATE,
                )
            )
        } catch (_: Exception) {
            recordLoad(
                JSONObject()
                    .put("stage", "WORKER_BIND")
                    .put("diagnostic_code", "WORKER_BIND_FAILED"),
                "FAILED",
            )
            cancel("worker_bind_failed", preserveStatus = true)
            session.failLoad()
            loadFailure = "The phone could not open the model worker. Export Research Results."
            status.text = loadFailure
            updateControls()
        }
    }

    private fun failureMessage(stage: String): String =
        when (stage) {
            "PACK_STATE" ->
                "The installed test pack could not be opened. Export Research Results so we can check it."
            "PACK_HASHES" ->
                "An installed test-pack file failed its check. Export Research Results before reinstalling anything."
            "NATIVE_RUNTIME" ->
                "The phone could not load the local translation engine. Export Research Results."
            "TRANSLATION_MODEL" ->
                "The translation model could not be loaded. Export Research Results."
            "ASR_MODEL" ->
                "The speech-recognition model could not be loaded. Export Research Results."
            "ASR" -> "Speech recognition failed. Export Research Results."
            "TRANSLATION" -> "Translation failed. Export Research Results."
            else -> "The local model worker could not finish. Export Research Results."
        }

    private fun continuePermissionLoad() {
        if (
            permissionLoadPending &&
                foreground &&
                packPresent() &&
                speech.languagesReady() &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        ) {
            permissionLoadPending = false
            safely { bindModels() }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 5) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            main.post { continuePermissionLoad() }
        else {
            permissionLoadPending = false
            status.text = "Microphone access is needed for speech. Tap Start Testing to try again."
            updateControls()
        }
    }

    private fun send(command: String, payload: JSONObject = JSONObject()) {
        val endpoint = worker ?: error("Inference worker unavailable")
        payload.put("run_id", runId).put("turn_index", turnIndex).put("mode", activeMode.name)
        endpoint.send(
            Message.obtain().apply {
                replyTo = replies
                data =
                    Bundle().apply {
                        putString("command", command)
                        putString("payload", payload.toString())
                    }
            }
        )
    }

    private fun replay(result: JSONObject, release: Long?, recordTurn: Boolean = true) {
        val epoch = generation
        val direction = result.getString("direction")
        val checkpointTurn = turnIndex
        val source = DiagnosticSource.valueOf(result.getString("source_kind").uppercase())
        traceUi(PipelineStage.TTS_START, source, direction)
        speech.speak(
            result.getString("translation"),
            direction.takeLast(2),
            release,
            { tts ->
                if (epoch != generation) return@speak
                result.put("tts", tts).put("ui_memory", Measurements.snapshot(this))
                timing.text =
                    "ASR: ${result.optDouble("asr_ms")} ms\nTranslation: ${result.optDouble("translation_ms")} ms\nTTS synthesis: ${tts.optDouble("synthesis_ms")} ms\nRelease → audio: ${tts.opt("release_to_audio_ms")} ms\n${tts.optString("audio_start_basis")}"
                status.text =
                    if (diagnosticRunning) "Full recorded-sample check: playing offline speech…"
                    else "Playing offline speech. Source audio deleted."
                updateControls()
                // Metrics omit human source/translation text. Explicit Save is separate.
                if (recordTurn)
                    metric(
                        "turn",
                        JSONObject()
                            .put("source_kind", result.getString("source_kind"))
                            .put("direction", direction)
                            .put("resident_turn_index", result.getInt("resident_turn_index"))
                            .put(
                                "first_after_model_load",
                                result.getBoolean("first_after_model_load"),
                            )
                            .put("asr_ms", result.getDouble("asr_ms"))
                            .put("translation_ms", result.getDouble("translation_ms"))
                            .put("speech_seconds", result.getDouble("speech_seconds"))
                            .put("tts", tts),
                    )
                if (recordTurn && fixturePlan != null) {
                    fixtureResults.put(result)
                    main.postDelayed(
                        { if (epoch == generation && foreground) safely { nextFixture() } },
                        tts.getDouble("playback_duration_ms").toLong() + 500,
                    )
                }
            },
            {
                if (epoch == generation && checkpointTurn == turnIndex) {
                    session.finishTurn()
                    traceUi(PipelineStage.TTS_COMPLETE, source, direction)
                    if (diagnosticRunning)
                        finishDiagnostic(
                            JSONObject()
                                .put("asr_ms", result.getDouble("asr_ms"))
                                .put("translation_ms", result.getDouble("translation_ms"))
                        )
                    else {
                        status.text = "Ready to Speak. Playback finished."
                        updateControls()
                    }
                }
            },
            { error ->
                if (epoch == generation) {
                    if (diagnosticRunning) recordDiagnosticResult("FAILED")
                    diagnosticRunning = false
                    session.finishTurn()
                    status.text = error.message
                    metric(
                        "error",
                        JSONObject()
                            .put("stage", "TTS")
                            .put("diagnostic_code", error.code.name)
                            .put("error_type", error.type),
                    )
                    fixturePlan = null
                    updateControls()
                }
            },
        )
    }

    private fun createSpeech(): OfflineSpeech =
        OfflineSpeech(
            this,
            { report ->
                if (!destroyed) {
                    writeTtsReport("tts-diagnostics.json", report)
                    if (report.optString("stage") == "ENGINE_INIT")
                        writeTtsReport("tts-engine-diagnostics.json", report)
                    if (report.optString("outcome") == "FAILED")
                        writeTtsReport("tts-failure-diagnostics.json", report)
                    metric("tts_diagnostic", report)
                }
            },
        ) {
            if (!processing && !importing && !selectingPack && !speechCheckRunning)
                refreshReadiness()
        }

    private fun writeTtsReport(name: String, report: JSONObject) {
        val file = AtomicFile(File(filesDir, name))
        val stream = file.startWrite()
        try {
            stream.write(report.toString(2).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun checkOfflineSpeech() {
        check(
            foreground &&
                !processing &&
                !session.recording &&
                !importing &&
                !selectingPack &&
                !switchingWorker &&
                !speechCheckRunning
        )
        speechCheckRunning = true
        val epoch = generation
        val results = JSONArray()
        val checks = listOf("en" to true, "es" to true, "en" to false, "es" to false)
        fun save(state: String) {
            writeTtsReport(
                "tts-checks.json",
                JSONObject()
                    .put("state", state)
                    .put("checks", results)
                    .put("audibility", "REQUIRES_HUMAN_OBSERVATION"),
            )
        }
        save("RUNNING")
        updateControls()
        fun next(index: Int) {
            if (epoch != generation || !foreground || !speechCheckRunning) return
            if (index == checks.size) {
                speechCheckRunning = false
                save("COMPLETE")
                updateControls()
                status.text =
                    "Speech check complete. Export Research Results and tell us which phrases you heard."
                return
            }
            val (language, direct) = checks[index]
            status.text =
                "Speech check ${index + 1} of 4: ${if (language == "en") "English" else "Spanish"}. Please listen."
            fun finish(result: String, error: TtsFailure? = null) {
                if (epoch != generation || !foreground || !speechCheckRunning) return
                results.put(
                    JSONObject()
                        .put("language", language)
                        .put("operation", if (direct) "DIRECT_SPEAK" else "SYNTHESIZE_FILE")
                        .put("result", result)
                        .apply {
                            if (error != null)
                                put("diagnostic_code", error.code.name)
                                    .put("error_type", error.type)
                        }
                )
                save("RUNNING")
                main.postDelayed({ next(index + 1) }, 500)
            }
            speech.speak(
                if (language == "en") "Common Tongue offline speech check."
                else "Prueba de voz sin conexión de Common Tongue.",
                language,
                null,
                {},
                { finish("PASS") },
                { finish("FAILED", it) },
                direct,
            )
        }
        next(0)
    }

    private fun startDiagnostic(mode: DiagnosticMode, diagnostic: Boolean = true) {
        check(!processing && !session.recording && !importing && !selectingPack && !switchingWorker)
        cancel("component_check_switch", preserveStatus = true)
        if (diagnostic) {
            model.setSelection(0)
            asr.setSelection(0)
        }
        // Allow the explicitly terminated old process to disappear before binding a
        // fresh component worker. No inference retry is performed.
        val epoch = generation
        switchingWorker = true
        status.text = "Preparing one component check…"
        updateControls()
        main.postDelayed(
            {
                if (epoch == generation && foreground) {
                    switchingWorker = false
                    safely { bindModels(mode, diagnostic) }
                }
            },
            1000,
        )
    }

    private fun runComponentCase() {
        session.beginTurn()
        turnIndex++
        released = null
        updateControls()
        val request = JSONObject().put("direction", "en-es")
        when (activeMode) {
            DiagnosticMode.WHISPER_ONLY -> {
                status.text = "Checking speech recognition on the installed recorded sample…"
                send("asr_only", request.put("file", "fixture-en01.wav"))
            }
            DiagnosticMode.MADLAD_ONLY -> {
                status.text = "Checking translation on a fixed English sentence…"
                send(
                    "text",
                    request.put(
                        "text",
                        "I can pick you up after work, but I cannot stay for dinner.",
                    ),
                )
            }
            DiagnosticMode.FULL_PIPELINE -> {
                status.text = "Checking recorded speech → translation → offline voice…"
                send("audio", request.put("file", "fixture-en01.wav"))
            }
        }
    }

    private fun recordDiagnosticResult(result: String, metrics: JSONObject = JSONObject()) {
        metrics.put("mode", activeMode.name).put("result", result)
        metric("component_check", metrics)
        val directory = File(filesDir, "diagnostics").apply { mkdirs() }
        File(directory, "check-${activeMode.name}.json").writeText(metrics.toString())
    }

    private fun finishDiagnostic(metrics: JSONObject) {
        recordDiagnosticResult("PASS", metrics)
        diagnosticRunning = false
        session.finishTurn()
        status.text =
            when (activeMode) {
                DiagnosticMode.WHISPER_ONLY ->
                    "Speech-recognition check complete. Next: tap 2. Check translation only."
                DiagnosticMode.MADLAD_ONLY ->
                    "Translation check complete. Next: tap 3. Check the full recorded-sample pipeline."
                DiagnosticMode.FULL_PIPELINE ->
                    "Full recorded-sample pipeline complete. You can now test the microphone."
            }
        updateControls()
    }

    private fun recordPipeline(data: JSONObject) {
        metric("pipeline_stage", data)
        File(filesDir, "pipeline-diagnostics.json").writeText(data.toString())
        diagnosticStatus.text =
            "Last pipeline boundary: ${data.getString("stage")}\nCheck: ${data.getString("mode")}"
    }

    private fun traceUi(
        stage: PipelineStage,
        source: DiagnosticSource,
        direction: String,
        samples: Int? = null,
    ) {
        val checkpoint =
            StageCheckpoint(
                runId,
                turnIndex,
                activeMode,
                source,
                direction,
                stage,
                samples,
                SystemClock.elapsedRealtime(),
            )
        StageJournal(File(filesDir, "diagnostics")).write("ui", checkpoint)
        recordPipeline(JSONObject(checkpoint.exportFields()))
    }

    private fun collectWorkerExit(
        pid: Int,
        started: Long,
        run: String,
        mode: DiagnosticMode,
        stage: String,
        aliveAtDisconnect: Boolean?,
    ) {
        val exitEpoch = generation
        val disconnectedElapsed = SystemClock.elapsedRealtime()
        fun attempt(index: Int) {
            if (destroyed) return
            exitExecutor.execute {
                val report =
                    WorkerExitDiagnostics.lookup(applicationContext, pid, started, run)
                        .put("mode", mode.name)
                        .put("last_pipeline_stage", stage)
                        .put("lookup_attempt", index + 1)
                        .put("disconnect_elapsed_ms", disconnectedElapsed)
                        .put("worker_alive_at_disconnect", aliveAtDisconnect ?: JSONObject.NULL)
                        .put("cleanup_requested_after_disconnect", true)
                // No raw PID/name/description/trace enters this file or the metrics.
                val destination = File(filesDir, "worker-exit-diagnostics.json")
                val previousDisconnect =
                    try {
                        JSONObject(destination.readText()).optLong("disconnect_elapsed_ms")
                    } catch (_: Exception) {
                        0L
                    }
                if (previousDisconnect <= disconnectedElapsed)
                    destination.writeText(report.toString())
                main.post {
                    if (destroyed) return@post
                    metric("worker_exit_info", report)
                    if (exitEpoch == generation)
                        diagnosticStatus.text =
                            "Worker stopped after $stage.\nAndroid exit reason: ${report.optString("reason", report.getString("availability"))}."
                    if (report.optString("availability") == "NOT_YET_AVAILABLE" && index < 3)
                        main.postDelayed({ attempt(index + 1) }, listOf(500L, 1500L, 3000L)[index])
                }
            }
        }
        attempt(0)
    }

    private fun startFixtures() {
        check(session.canSpeak && capture == null) {
            "Wait for Ready to Speak and the current turn to finish."
        }
        fixturePlan =
            JSONObject(File(ResearchFiles.root(this), "trial-plan.json").readText())
                .getJSONArray("cases")
        fixtureIndex = 0
        fixtureResults = JSONArray()
        nextFixture()
    }

    private fun nextFixture() {
        val plan = fixturePlan ?: return
        if (fixtureIndex == plan.length()) {
            val directory = File(filesDir, "results").apply { mkdirs() }
            File(directory, "fixtures-${model.selectedItem}-${asr.selectedItem}.json")
                .writeText(
                    JSONObject()
                        .put("cases", fixtureResults)
                        .put("offline", Measurements.offline(this))
                        .toString(2)
                )
            fixturePlan = null
            status.text = "Fixed checks finished; authored fixture results saved locally."
            updateControls()
            return
        }
        val item = plan.getJSONObject(fixtureIndex++)
        turnIndex++
        released = if (item.has("file")) Measurements.now() else null
        session.beginTurn()
        updateControls()
        status.text = "Fixed check ${fixtureIndex}/${plan.length()}: ${item.getString("case_id")}"
        send(if (item.has("file")) "audio" else "text", item)
    }

    private fun metric(event: String, data: JSONObject) {
        val directory = File(filesDir, "metrics").apply { mkdirs() }
        File(directory, "session.jsonl")
            .appendText(
                JSONObject()
                    .put("event", event)
                    .put("elapsed_ms", SystemClock.elapsedRealtime())
                    .put("data", data)
                    .toString() + "\n"
            )
    }

    private fun cleanTransient() {
        File(filesDir, "audio")
            .listFiles()
            ?.filter { it.name.matches(Regex("capture-[a-f0-9]{32}\\.wav")) }
            ?.forEach { it.delete() }
        File(filesDir, "speech")
            .listFiles()
            ?.filter { it.extension == "wav" }
            ?.forEach { it.delete() }
    }

    private fun cancel(reason: String, preserveStatus: Boolean = false) {
        switchingWorker = false
        stopCapture(false)
        if (
            session.phase == ResearchSession.Phase.LOADING_MODELS &&
                reason != "inference_error" &&
                reason != "worker_disconnect" &&
                reason != "worker_bind_failed"
        )
            recordLoad(JSONObject().put("diagnostic_code", "LOAD_INTERRUPTED"), "INTERRUPTED")
        generation++
        fixturePlan = null
        diagnosticRunning = false
        if (speechCheckRunning) {
            speechCheckRunning = false
            try {
                val report =
                    JSONObject(File(filesDir, "tts-checks.json").readText())
                        .put("state", "CANCELLED")
                writeTtsReport("tts-checks.json", report)
            } catch (_: Exception) {}
        }
        session.unload()
        released = null
        if (::speech.isInitialized) speech.cancel()
        try {
            worker?.send(
                Message.obtain().apply {
                    data = Bundle().apply { putString("command", "terminate") }
                }
            )
        } catch (_: Exception) {}
        connection?.let {
            try {
                unbindService(it)
            } catch (_: Exception) {}
        }
        connection = null
        model.isEnabled = true
        asr.isEnabled = true
        worker = null
        replies = null
        cleanTransient()
        metric(
            "cancel_or_unload",
            JSONObject().put("reason", reason).put("ui_memory", Measurements.snapshot(this)),
        )
        main.postDelayed({ metric("after_unload_ui", Measurements.snapshot(this)) }, 1500)
        updateControls()
        if (!preserveStatus) {
            refreshReadiness()
            if (packPresent())
                status.text = "Pack Installed. Models unloaded; tap Start Testing to load them."
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        if (settingUpVoices) {
            settingUpVoices = false
            speech.close()
            speech = createSpeech()
        }
        main.post(samples)
        if (::status.isInitialized) refreshReadiness()
        updateRadioStatus()
        continuePermissionLoad()
    }

    override fun onPause() {
        foreground = false
        main.removeCallbacks(samples)
        if (importing) importCancelled.set(true)
        cancel("activity_background", preserveStatus = importing || selectingPack)
        super.onPause()
    }

    override fun onDestroy() {
        destroyed = true
        importCancelled.set(true)
        importExecutor.shutdownNow()
        exitExecutor.shutdownNow()
        if (::speech.isInitialized) speech.close()
        super.onDestroy()
    }
}
