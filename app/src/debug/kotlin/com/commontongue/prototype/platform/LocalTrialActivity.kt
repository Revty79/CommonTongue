package com.commontongue.prototype.platform

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.commontongue.domain.LanguageId
import com.commontongue.domain.TranslationDirection
import com.commontongue.local.*
import com.commontongue.local.android.LocalAudioSource
import com.commontongue.local.android.LocalCapabilities
import com.commontongue.prototype.BuildConfig
import com.commontongue.translation.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

/** Temporary component checks. No microphone, conversation controller, or Pass 8 UI. */
class LocalTrialActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var status: TextView
    private lateinit var setup: Button
    private lateinit var run: Button
    private lateinit var testResources: TestResources
    private lateinit var capabilities: LocalCapabilities
    private var task: Job? = null
    private var operation: Job? = null
    private var cancelAt: LocalStage? = null
    private val events = JSONArray()
    private val checks = JSONArray()
    private val controls by lazy {
        JSONArray(assets.open("pass7/controls.json").bufferedReader().use { it.readText() })
    }
    private val export =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri
            ->
            if (uri != null)
                try {
                    contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        it.write(results().toString(2))
                    }
                    status.text = "Results exported."
                } catch (_: Exception) {
                    status.text = "Results could not be saved. Please try again."
                }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        testResources = TestResources(this)
        capabilities =
            LocalCapabilities.create(
                this,
                testResources.source,
                LocalAudioSource { testResources.fixture(it.token) },
                ::event,
            )
        val layout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 36, 32, 32)
            }
        layout.addView(
            TextView(this).apply {
                text =
                    "Common Tongue â€” Pass 7 internal checks\n${BuildConfig.VERSION_NAME}\n\nSet up once, then run checks. Leave the app open. These checks use fixed test phrases; no microphone recording. Radios may be on or off."
            }
        )
        status =
            TextView(this).apply {
                text =
                    if (testResources.installed()) "Resources installed â€” ready to load."
                    else "Set up the offline test testResources first."
            }
        layout.addView(status)
        setup =
            button(layout, "Set up test testResources") {
                task = scope.launch {
                    busy(true)
                    try {
                        capabilities.lifecycle.release()
                        var previous = ""
                        testResources.install { value ->
                            if (value != previous) {
                                previous = value
                                runOnUiThread { status.text = value }
                            }
                        }
                        status.text =
                            "Installation complete â€” ready for checks. You can turn off radios for an offline proof."
                    } catch (cancelled: CancellationException) {
                        status.text = "Setup stopped. Tap setup to try again."
                        throw cancelled
                    } catch (fault: SetupFault) {
                        status.text = fault.plain
                    } catch (_: Throwable) {
                        status.text = "Setup could not finish. Please try again."
                    } finally {
                        busy(false)
                    }
                }
            }
        run =
            button(layout, "Run EN + ES adapter checks") {
                task = scope.launch {
                    busy(true)
                    checks.length().let { repeat(it) { checks.remove(0) } }
                    events.length().let { repeat(it) { events.remove(0) } }
                    try {
                        runChecks()
                        status.text = "Checks finished. Export results for review."
                    } catch (cancelled: CancellationException) {
                        status.text = "Checks stopped. You can run them again."
                        throw cancelled
                    } catch (fault: LocalFault) {
                        status.text = fault.reason.explanation
                    } catch (_: Throwable) {
                        status.text =
                            "The local checks could not finish. Export results and try again."
                        checks.put(
                            JSONObject()
                                .put("check", "RUN")
                                .put("status", "FAILED")
                                .put("failure", LocalFailure.NATIVE_FAILED.name)
                        )
                    } finally {
                        cancelAt = null
                        busy(false)
                    }
                }
            }
        button(layout, "Stop / release models") {
            scope.launch {
                task?.cancelAndJoin()
                capabilities.lifecycle.release()
                status.text = "Models released. Run checks to load again."
            }
        }
        button(layout, "Accepted offline voice checks") {
            startActivity(Intent(this, VoiceTrialActivity::class.java))
        }
        button(layout, "Export results") {
            export.launch("CommonTongue-Pass7-Adapter-Results.json")
        }
        setContentView(ScrollView(this).apply { addView(layout) })
        run.isEnabled = testResources.installed()
    }

    private fun button(layout: LinearLayout, label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { action() }
            layout.addView(this)
        }

    private fun busy(value: Boolean) {
        if (value) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setup.isEnabled = !value
        run.isEnabled = !value && testResources.installed()
    }

    private fun event(value: LocalDiagnostic) {
        if (events.length() < 3000)
            events.put(
                JSONObject()
                    .put("stage", value.stage.name)
                    .put("milliseconds", value.milliseconds)
                    .put("failure", value.failure?.name)
                    .put("worker_pss_bytes", value.workerPssBytes)
                    .put("worker_rss_bytes", value.workerRssBytes)
                    .put("exit_reason", value.exitReason)
                    .put("exit_status", value.exitStatus)
            )
        when (value.stage) {
            LocalStage.RESOURCE_VALIDATE_START ->
                status.text = "Checking installed testResourcesâ€¦"
            LocalStage.MODEL_LOAD_START -> status.text = "Loading modelsâ€¦"
            LocalStage.MODEL_LOAD_COMPLETE ->
                status.text = "Models ready â€” running fixed checksâ€¦"
            else -> Unit
        }
        if (cancelAt == value.stage) {
            cancelAt = null
            operation?.cancel()
        }
    }

    private suspend fun runChecks() {
        capabilities.lifecycle.prepare()
        for (index in 0 until controls.length()) {
            val control = controls.getJSONObject(index)
            asr(control, "ASR_${control.getString("language").uppercase()}")
            translate(control, "MT_${control.getString("language").uppercase()}")
        }
        // Repeated use preserves one resident owner, then explicit release forces a new load.
        for (index in 0 until controls.length()) {
            val control = controls.getJSONObject(index)
            asr(control, "ASR_REPEAT_${control.getString("language").uppercase()}")
            translate(control, "MT_REPEAT_${control.getString("language").uppercase()}")
        }
        cancelCheck(LocalStage.ASR_START, "CANCEL_ASR") { asrResult(controls.getJSONObject(0)) }
        asr(controls.getJSONObject(0), "ASR_RETRY")
        cancelCheck(LocalStage.ENCODER_START, "CANCEL_MT") {
            translationResult(controls.getJSONObject(0))
        }
        translate(controls.getJSONObject(0), "MT_RETRY")
        capabilities.lifecycle.release()
        capabilities.lifecycle.prepare()
        asr(controls.getJSONObject(1), "ASR_RELOAD")
        translate(controls.getJSONObject(1), "MT_RELOAD")
        capabilities.lifecycle.release()
    }

    private suspend fun asrResult(control: JSONObject) =
        capabilities.recognizer.recognize(
            RecognitionRequest(
                AudioReference.of(control.getString("audio_token")),
                LanguageSelection.Locked(LanguageId.parse(control.getString("language"))),
            )
        )

    private suspend fun translationResult(control: JSONObject): TranslationResult {
        val source = LanguageId.parse(control.getString("language"))
        return capabilities.translator.translate(
            TranslationRequest(
                control.getString("source"),
                TranslationDirection(
                    source,
                    LanguageId.parse(if (source.baseLanguage == "en") "es" else "en"),
                ),
            )
        )
    }

    private suspend fun asr(control: JSONObject, name: String) {
        val start = System.nanoTime()
        when (val result = asrResult(control)) {
            is CapabilityResult.Failure -> {
                record(name, "FAILED", start, result.failure.category.name)
                throw LocalFault(LocalFailure.NATIVE_FAILED)
            }
            is CapabilityResult.Success -> {
                val reference = control.optString("expected_asr")
                val match = reference.isNotEmpty() && result.value.text.trim() == reference
                record(name, if (match) "PASS" else "PARITY_DIFFERENCE", start)
                if (!match) throw LocalFault(LocalFailure.INVALID_RESULT)
            }
        }
    }

    private suspend fun translate(control: JSONObject, name: String) {
        val start = System.nanoTime()
        when (val result = translationResult(control)) {
            is CapabilityResult.Failure -> {
                record(name, "FAILED", start, result.failure.category.name)
                throw LocalFault(LocalFailure.NATIVE_FAILED)
            }
            is CapabilityResult.Success -> {
                val match =
                    result.value.text == control.getString("expected_translation") &&
                        result.value.completion == CompletionStatus.COMPLETE
                record(name, if (match) "PASS" else "PARITY_DIFFERENCE", start)
                if (!match)
                    throw LocalFault(
                        LocalFailure.INVALID_RESULT
                    ) // Stop; never change accepted expected text.
            }
        }
    }

    private suspend fun cancelCheck(stage: LocalStage, name: String, action: suspend () -> Any) =
        coroutineScope {
            val start = System.nanoTime()
            cancelAt = stage
            val request = async(start = CoroutineStart.LAZY) { action() }
            operation = request
            request.start()
            try {
                request.await()
                record(name, "FAILED", start)
                throw LocalFault(LocalFailure.INVALID_RESULT)
            } catch (_: CancellationException) {
                currentCoroutineContext().ensureActive()
                record(name, "PASS", start)
            } finally {
                operation = null
                cancelAt = null
            }
        }

    private fun record(name: String, outcome: String, start: Long, failure: String? = null) {
        checks.put(
            JSONObject()
                .put("check", name)
                .put("status", outcome)
                .put("milliseconds", (System.nanoTime() - start) / 1_000_000)
                .put("failure", failure)
        )
    }

    private fun results() =
        JSONObject()
            .put("schema_version", 1)
            .put("scope", "PASS7_PRODUCTION_ADAPTER_FIXED_CONTROLS")
            .put("source_commit", BuildConfig.SPEECH_SOURCE_REVISION)
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("resource_version", LockedCore.VERSION)
            .put("checks", checks)
            .put("events", events)
            .put("human_content_exported", false)
            .put("physical_scope", "COMPONENTS_ONLY_NOT_PTT")

    override fun onStart() {
        super.onStart()
        if (::capabilities.isInitialized)
            scope.launch { capabilities.lifecycle.setForeground(true) }
    }

    override fun onStop() {
        task?.cancel()
        if (::capabilities.isInitialized)
            scope.launch { capabilities.lifecycle.setForeground(false) }
        super.onStop()
    }

    override fun onDestroy() {
        scope.launch(NonCancellable) {
            if (::capabilities.isInitialized) capabilities.lifecycle.close()
        }
        scope.cancel()
        super.onDestroy()
    }
}
