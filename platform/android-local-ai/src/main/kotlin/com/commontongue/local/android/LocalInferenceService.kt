package com.commontongue.local.android

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import com.commontongue.local.CoreResourceRole
import com.commontongue.local.LocalFailure
import com.commontongue.local.LocalFault
import com.commontongue.local.LocalStage
import java.io.File
import java.util.concurrent.Executors
import org.json.JSONObject

/** Sole owner of all native contexts. Blocking kernels never run in the UI process. */
class LocalInferenceService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var asr = 0L
    private var translator = 0L
    private var busy = false
    private val receiver =
        Messenger(
            Handler(Looper.getMainLooper()) { message ->
                when (message.what) {
                    HELLO -> reply(message, JSONObject().put("worker", Process.myPid()))
                    TERMINATE -> Process.killProcess(Process.myPid())
                    else -> {
                        if (busy)
                            reply(
                                message,
                                JSONObject().put("failure", LocalFailure.NATIVE_FAILED.name),
                            )
                        else {
                            busy = true
                            val command = message.what
                            val id = message.arg1
                            val client = message.replyTo
                            val payload = message.data.getString("payload") ?: "{}"
                            executor.execute {
                                val start = System.nanoTime()
                                val result =
                                    try {
                                        execute(command, JSONObject(payload)) { stage ->
                                            send(
                                                client,
                                                EVENT,
                                                id,
                                                JSONObject().put("stage", stage.name),
                                            )
                                        }
                                    } catch (error: Throwable) {
                                        JSONObject()
                                            .put(
                                                "failure",
                                                when (error) {
                                                    is LocalFault -> error.reason
                                                    is LinkageError ->
                                                        LocalFailure.RUNTIME_UNAVAILABLE
                                                    is OutOfMemoryError ->
                                                        LocalFailure.RESOURCE_LIMIT
                                                    else -> LocalFailure.NATIVE_FAILED
                                                }.name,
                                            )
                                    }
                                result.put("milliseconds", (System.nanoTime() - start) / 1_000_000)
                                val memory = Debug.MemoryInfo()
                                Debug.getMemoryInfo(memory)
                                result.put("pss", memory.totalPss.toLong() * 1024)
                                // Numeric memory measurement only; no PID/maps/paths leave this
                                // protocol.
                                main.post {
                                    busy = false
                                    send(client, RESULT, id, result)
                                }
                            }
                        }
                    }
                }
                true
            }
        )

    override fun onBind(intent: Intent?): IBinder = receiver.binder

    private fun execute(command: Int, input: JSONObject, event: (LocalStage) -> Unit): JSONObject =
        when (command) {
            LOAD -> {
                if (!Process.is64Bit()) throw LocalFault(LocalFailure.RUNTIME_UNAVAILABLE)
                val paths =
                    CoreResourceRole.entries.associateWith { role ->
                        File(input.getString(role.name))
                    }
                for ((role, file) in paths) {
                    val identity = com.commontongue.local.LockedCore.identities.getValue(role)
                    if (!file.isFile || file.length() != identity.bytes)
                        throw LocalFault(LocalFailure.WRONG_RESOURCES)
                }
                // The resource binding owns physical locations. The C ABI's filenames stay here.
                val directory = File(cacheDir, "local-core-bindings").apply { mkdirs() }
                for ((role, name) in
                    mapOf(
                        CoreResourceRole.TRANSLATOR to "model-q4k.gguf",
                        CoreResourceRole.TOKENIZER to "tokenizer.json",
                        CoreResourceRole.CONFIGURATION to "config.json",
                    )) {
                    val link = File(directory, name)
                    if (link.exists() || java.nio.file.Files.isSymbolicLink(link.toPath()))
                        link.delete()
                    android.system.Os.symlink(paths.getValue(role).absolutePath, link.absolutePath)
                }
                val model = JSONObject(Native.t5Load(directory.absolutePath))
                if (model.has("error") || model.optString("handle_kind") != "opaque-id-v1")
                    throw LocalFault(LocalFailure.LOAD_FAILED)
                val raw = model.get("handle")
                if (raw !is Int && raw !is Long) throw LocalFault(LocalFailure.INVALID_RESULT)
                translator = (raw as Number).toLong()
                if (translator !in 1L..0xffffffffL) throw LocalFault(LocalFailure.INVALID_RESULT)
                asr = Native.asrCreate(paths.getValue(CoreResourceRole.RECOGNIZER).absolutePath)
                if (asr !in 1L..0xffffffffL) throw LocalFault(LocalFailure.LOAD_FAILED)
                JSONObject()
            }
            ASR -> {
                if (asr == 0L) throw LocalFault(LocalFailure.LOAD_FAILED)
                val language = input.getString("language")
                if (language !in setOf("en", "es"))
                    throw LocalFault(LocalFailure.LANGUAGE_UNSUPPORTED)
                val audio = PcmInput.read(File(input.getString("audio")))
                event(LocalStage.ASR_START)
                val text = Native.asrRun(asr, audio, language).trim()
                event(LocalStage.ASR_COMPLETE)
                JSONObject().put("text", text).put("language", language).put("complete", true)
            }
            TRANSLATE -> {
                if (translator == 0L) throw LocalFault(LocalFailure.LOAD_FAILED)
                event(LocalStage.TRANSLATION_START)
                val output =
                    JSONObject(
                        Native.t5Run(
                            translator,
                            input.toString(),
                            NativeProgress { code ->
                                val stage =
                                    when (code) {
                                        1 -> LocalStage.TOKENIZE_START
                                        2 -> LocalStage.TOKENIZE_COMPLETE
                                        3 -> LocalStage.ENCODER_START
                                        4 -> LocalStage.ENCODER_COMPLETE
                                        5 -> LocalStage.DECODER_START
                                        6 -> LocalStage.FIRST_TOKEN
                                        else -> null
                                    }
                                if (stage != null) event(stage)
                            },
                        )
                    )
                if (output.has("error")) {
                    // Inspect only this fixed runtime code; never propagate arbitrary native
                    // messages.
                    if (output.optString("error") == "Input exceeds 512 tokens; no truncation")
                        throw LocalFault(LocalFailure.INPUT_TOO_LONG)
                    throw LocalFault(LocalFailure.NATIVE_FAILED)
                }
                event(LocalStage.TRANSLATION_COMPLETE)
                JSONObject()
                    .put("text", output.getString("text"))
                    .put("complete", output.getBoolean("completed"))
            }
            else -> throw LocalFault(LocalFailure.INPUT_INVALID)
        }

    private fun reply(message: Message, value: JSONObject) =
        send(message.replyTo, RESULT, message.arg1, value)

    private fun send(client: Messenger?, kind: Int, id: Int, value: JSONObject) {
        try {
            client?.send(
                Message.obtain().apply {
                    what = kind
                    arg1 = id
                    data = Bundle().apply { putString("payload", value.toString()) }
                }
            )
        } catch (_: Throwable) {
            /* A dead client cannot break native work; owner will be reclaimed. */
        }
    }

    override fun onDestroy() {
        // Service teardown is process teardown: do not free a context under a running kernel.
        executor.shutdownNow()
        super.onDestroy()
        Process.killProcess(Process.myPid())
    }

    internal companion object {
        const val HELLO = 1
        const val LOAD = 2
        const val ASR = 3
        const val TRANSLATE = 4
        const val TERMINATE = 5
        const val RESULT = 10
        const val EVENT = 11
    }
}
