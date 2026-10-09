package com.commontongue.prototype.platform

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.commontongue.domain.LanguageId
import com.commontongue.prototype.BuildConfig
import com.commontongue.speech.android.AndroidSpeechSynthesizer
import com.commontongue.speech.android.SpeechDiagnostic
import com.commontongue.speech.android.SpeechStage
import com.commontongue.translation.AudioReference
import com.commontongue.translation.CapabilityResult
import com.commontongue.translation.FailureCategory
import com.commontongue.translation.SpeechSynthesizer
import com.commontongue.translation.SynthesisRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Debug-only physical integration harness. Exercises the production adapter, never model runtimes.
 */
class VoiceTrialActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var speech: AndroidSpeechSynthesizer
    private lateinit var status: TextView
    private val events = ArrayDeque<SpeechDiagnostic>()
    private val results = JSONArray()
    private var replay: AudioReference? = null
    private var trial: Job? = null
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speech =
            AndroidSpeechSynthesizer(applicationContext) { event ->
                if (events.size == 512) events.removeFirst()
                events.addLast(event)
                if (event.stage == SpeechStage.PLAYBACK_START)
                    status.text = "Speech is playing through your phone's selected audio output."
            }
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val padding = (20 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
            }
        fun text(value: String) =
            TextView(this).also {
                it.text = value
                content.addView(it)
            }
        text("Common Tongue — Pass 6 Voice Check").textSize = 24f
        text("Playback correction · " + BuildConfig.VERSION_NAME)
        text(
            "Uses your installed offline voices. Wi-Fi and cellular may stay on. No model pack, microphone, account or development connection is needed."
        )
        status = text("Ready to check installed voices.")
        fun button(label: String, action: () -> Unit) {
            content.addView(
                Button(this).apply {
                    this.text = label
                    setOnClickListener { action() }
                }
            )
        }
        button("Run English + Spanish checks") {
            trial?.cancel()
            trial = scope.launch { runChecks() }
        }
        button("Hear English") { startTurn("MANUAL_EN", "en-US", ENGLISH) }
        button("Hear Spanish") { startTurn("MANUAL_ES", "es-419", SPANISH) }
        button("Replay last speech") {
            trial?.cancel()
            trial = scope.launch {
                val audio = replay
                if (audio == null) status.text = "Hear English or Spanish first."
                else record("MANUAL_REPLAY", speech.play(audio))
            }
        }
        button("Stop speaking") {
            trial?.cancel()
            scope.launch {
                speech.cancel()
                status.text = "Speech stopped."
            }
        }
        button("Open phone voice settings") {
            try {
                startActivity(Intent("com.android.settings.TTS_SETTINGS"))
            } catch (_: Exception) {
                status.text =
                    "Open Settings and search for Text-to-speech. Install offline English and Spanish voices."
            }
        }
        text(
            "This check plays English, Spanish, then repeats the Spanish speech. Export the results and report whether you heard all three. Keep your installed voices."
        )
        button("Export Pass 6 results") {
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "CommonTongue-Pass6-Voice-Results.json")
                },
                EXPORT,
            )
        }
        setContentView(
            ScrollView(this).apply {
                setOnApplyWindowInsetsListener { view, insets ->
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                        view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                    } else {
                        @Suppress("DEPRECATION")
                        view.setPadding(
                            insets.systemWindowInsetLeft,
                            insets.systemWindowInsetTop,
                            insets.systemWindowInsetRight,
                            insets.systemWindowInsetBottom,
                        )
                    }
                    insets
                }
                addView(
                    content,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        )
    }

    private fun startTurn(name: String, language: String, text: String) {
        trial?.cancel()
        trial = scope.launch { turn(name, language, text) }
    }

    private suspend fun turn(name: String, language: String, text: String) {
        status.text = "Preparing offline speech…"
        val contract: SpeechSynthesizer = speech
        val result = contract.synthesize(SynthesisRequest(text, LanguageId.parse(language)))
        if (result is CapabilityResult.Success) {
            replay = result.value.audio
            record(name + "_SYNTHESIS", result)
            record(name, speech.play(result.value.audio))
        } else record(name, result)
    }

    private suspend fun runChecks() {
        try {
            results.put(
                JSONObject().put("case", "RUN_CONTEXT").put("airplane_mode", airplaneMode())
            )
            turn("EN", "en-US", ENGLISH)
            turn("ES", "es-419", SPANISH)
            replay?.let { record("REPLAY", speech.play(it)) }
            status.text =
                "Checks finished. Export results, and confirm you heard English, Spanish and replay."
        } catch (error: CancellationException) {
            results.put(JSONObject().put("case", "CHECK_INTERRUPTED").put("outcome", "CANCELLED"))
            throw error
        }
    }

    private fun record(
        name: String,
        result: CapabilityResult<*>,
        expectedCategory: FailureCategory? = null,
    ) {
        val failure = (result as? CapabilityResult.Failure)?.failure
        val success = result is CapabilityResult.Success
        val expected =
            if (expectedCategory != null) failure?.category == expectedCategory else success
        results.put(
            JSONObject()
                .put("case", name)
                .put("outcome", if (expected) "PASS" else "FAILED")
                .put("expected_failure", expectedCategory != null)
                .put("expected_failure_category", expectedCategory?.name ?: JSONObject.NULL)
                .put("failure_category", failure?.category?.name ?: JSONObject.NULL)
                .put("failure", failure?.explanation ?: JSONObject.NULL)
                .put(
                    "execution",
                    (result as? CapabilityResult.Success)?.executionMode?.name ?: JSONObject.NULL,
                )
        )
        status.text =
            if (expectedCategory != null && expected) "The unavailable option check passed."
            else if (success) "Speech finished."
            else failure?.explanation?.substringAfter(": ") ?: "This check did not finish."
    }

    private fun airplaneMode(): Boolean =
        Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    private fun export(): String {
        val diagnostics = JSONArray()
        events.forEach { event ->
            diagnostics.put(
                JSONObject()
                    .put("stage", event.stage.name)
                    .put("engine", event.engine ?: JSONObject.NULL)
                    .put("voice", event.voice ?: JSONObject.NULL)
                    .put("locale", event.locale ?: JSONObject.NULL)
                    .put("requires_network", event.requiresNetwork ?: JSONObject.NULL)
                    .put("milliseconds", event.milliseconds ?: JSONObject.NULL)
                    .put("frames", event.frames ?: JSONObject.NULL)
                    .put("code", event.code?.name ?: JSONObject.NULL)
                    .put("android_code", event.androidCode ?: JSONObject.NULL)
                    .put("audio_route_type", event.routeType ?: JSONObject.NULL)
                    .put("playback_step", event.playback?.step?.name ?: JSONObject.NULL)
                    .put("playback_outcome", event.playback?.outcome?.name ?: JSONObject.NULL)
                    .put("audio_track_state", event.playback?.trackState ?: JSONObject.NULL)
                    .put("audio_track_play_state", event.playback?.playState ?: JSONObject.NULL)
                    .put("pcm_bytes", event.playback?.pcmBytes ?: JSONObject.NULL)
                    .put("pcm_channels", event.playback?.channels ?: JSONObject.NULL)
                    .put("pcm_sample_rate_hz", event.playback?.sampleRateHz ?: JSONObject.NULL)
                    .put("audio_focus_result", event.playback?.focusResult ?: JSONObject.NULL)
                    .put("audio_track_write_result", event.playback?.writeResult ?: JSONObject.NULL)
                    .put("playback_head_frames", event.playback?.frames ?: JSONObject.NULL)
                    .put("playback_route_type", event.playback?.routeType ?: JSONObject.NULL)
                    .put("playback_elapsed_ms", event.playback?.milliseconds ?: JSONObject.NULL)
                    .put("exception_type", event.playback?.exceptionType?.name ?: JSONObject.NULL)
            )
        }
        return JSONObject()
            .put("schema_version", 2)
            .put("scope", "PASS_6_PRODUCTION_ADAPTER_PHYSICAL_CHECK")
            .put("device_model", android.os.Build.MODEL)
            .put("android_api", android.os.Build.VERSION.SDK_INT)
            .put("app_version", packageManager.getPackageInfo(packageName, 0).versionName)
            .put("source_commit", BuildConfig.SPEECH_SOURCE_REVISION)
            .put("adapter", "SpeechSynthesizer/AndroidSpeechSynthesizer")
            .put("path", "SYNTHESIZE_FILE_PCM16_AUDIOTRACK")
            .put("internet_permission", false)
            .put("airplane_mode_at_export", airplaneMode())
            .put("results", results)
            .put("diagnostics", diagnostics)
            .put("human_audibility", "REQUIRES_TESTER_CONFIRMATION")
            .toString(2)
    }

    @Deprecated("Legacy activity result API is sufficient for this debug-only check")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EXPORT && resultCode == RESULT_OK) {
            try {
                val uri = data?.data ?: return
                contentResolver.openOutputStream(uri)?.use {
                    it.write(export().toByteArray(Charsets.UTF_8))
                } ?: throw java.io.IOException()
                status.text = "Results exported."
            } catch (_: Exception) {
                status.text =
                    "The results could not be saved. Choose another location and try again."
            }
        }
    }

    override fun onStart() {
        super.onStart()
        scope.launch {
            speech.setForeground(true)
            if (resumed) status.text = "Welcome back. Replay is available, or run the checks again."
            resumed = true
        }
    }

    override fun onStop() {
        trial?.cancel()
        scope.launch { speech.setForeground(false) }
        super.onStop()
    }

    override fun onDestroy() {
        scope.launch {
            speech.close()
            scope.cancel()
        }
        super.onDestroy()
    }

    private companion object {
        const val EXPORT = 61
        const val ENGLISH = "Hello. Common Tongue is using an installed offline English voice."
        const val SPANISH = "Hola. Common Tongue usa una voz de español instalada y sin conexión."
    }
}
