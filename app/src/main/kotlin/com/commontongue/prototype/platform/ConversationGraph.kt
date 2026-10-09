package com.commontongue.prototype.platform

import android.content.Context
import com.commontongue.local.CoreResourceRole
import com.commontongue.local.LocalDiagnostic
import com.commontongue.local.LocalFault
import com.commontongue.local.android.FileCoreResources
import com.commontongue.local.android.LocalCapabilities
import com.commontongue.speech.android.AndroidSpeechSynthesizer
import com.commontongue.speech.android.SpeechDiagnostic
import com.commontongue.translation.*
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import org.json.JSONArray
import org.json.JSONObject

/** The only product composition root. Existing Pass 7 storage and accepted adapters are reused. */
internal class ConversationGraph(context: Context, scope: CoroutineScope) {
    private val microphone = MicrophoneInput(context.applicationContext)
    private val resolver = context.applicationContext.contentResolver
    private val core = InstalledCoreResources(context)
    private val events = ArrayDeque<JSONObject>()
    private val ai = LocalCapabilities.create(context, core.source, microphone, ::localEvent)
    private val speech = AndroidSpeechSynthesizer(context, ::speechEvent)
    val turns =
        ConversationTurnCoordinator(
            scope,
            microphone,
            ai.recognizer,
            ai.translator,
            speech,
            LocalSpeaker(speech),
            object : ConversationResources {
                override suspend fun prepare(): CapabilityResult<Unit> {
                    if (!core.installed())
                        return CapabilityResult.Failure(
                            CapabilityFailure(FailureCategory.MODEL_NOT_INSTALLED)
                        )
                    return try {
                        ai.lifecycle.prepare()
                        CapabilityResult.Success(Unit, ExecutionMode.OFFLINE)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (fault: LocalFault) {
                        CapabilityResult.Failure(CapabilityFailure(fault.reason.category))
                    } catch (_: Exception) {
                        CapabilityResult.Failure(
                            CapabilityFailure(FailureCategory.ENGINE_NOT_AVAILABLE)
                        )
                    }
                }

                override suspend fun setForeground(value: Boolean) {
                    ai.lifecycle.setForeground(value)
                    speech.setForeground(value)
                }

                override suspend fun close() {
                    try {
                        ai.lifecycle.close()
                    } finally {
                        speech.close()
                    }
                }
            },
        ) {
            record(
                JSONObject()
                    .put("kind", "TURN")
                    .put("stage", it.stage.name)
                    .put("problem", it.problem?.name)
                    .put(
                        "airplane_mode",
                        android.provider.Settings.Global.getInt(
                            resolver,
                            android.provider.Settings.Global.AIRPLANE_MODE_ON,
                            0,
                        ) == 1,
                    )
            )
        }

    private fun localEvent(value: LocalDiagnostic) {
        record(
            JSONObject()
                .put("kind", "LOCAL")
                .put("stage", value.stage.name)
                .put("failure", value.failure?.name)
                .put("milliseconds", value.milliseconds)
                .put("memory_pss_bytes", value.workerPssBytes)
                .put("exit_reason", value.exitReason)
                .put("exit_status", value.exitStatus)
        )
    }

    private fun speechEvent(value: SpeechDiagnostic) {
        record(
            JSONObject()
                .put("kind", "SPEECH")
                .put("stage", value.stage.name)
                .put("code", value.code?.name)
                .put("milliseconds", value.milliseconds)
                .put("frames", value.frames)
                .put("requires_network", value.requiresNetwork)
        )
    }

    @Synchronized
    private fun record(value: JSONObject) {
        if (events.size == 600) events.removeFirst()
        events.addLast(value)
    }

    @Synchronized
    fun diagnostics(): String =
        JSONObject()
            .put("schema", "pass8-content-free-v1")
            .put("source_commit", com.commontongue.prototype.BuildConfig.SPEECH_SOURCE_REVISION)
            .put("version", com.commontongue.prototype.BuildConfig.VERSION_NAME)
            .put("pack_installed", core.installed())
            .put("events", JSONArray(events.toList()))
            .put("raw_speech_or_text_exported", false)
            .toString(2)
}

/** Same directory, token and role names as Pass 7. No acquisition or HTTP in production. */
internal class InstalledCoreResources(context: Context) {
    private val root = File(context.applicationContext.filesDir, "local-core")
    val source =
        FileCoreResources(
            "debug-core-v1",
            CoreResourceRole.entries.associateWith {
                File(root, it.name.lowercase())
            },
        )

    fun installed() = File(root, "installed").isFile
}
