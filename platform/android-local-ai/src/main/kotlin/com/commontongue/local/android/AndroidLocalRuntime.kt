package com.commontongue.local.android

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import com.commontongue.domain.LanguageId
import com.commontongue.domain.TranslationDirection
import com.commontongue.local.*
import com.commontongue.translation.AudioReference
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/** Serialized by LocalAiSession. All IPC state runs on the main dispatcher. */
internal class AndroidLocalRuntime(
    context: Context,
    private val resources: AndroidResourceBindings,
    private val audio: LocalAudioSource,
    private val diagnostic: (LocalDiagnostic) -> Unit,
) : LocalRuntime {
    private val app = context.applicationContext
    private var remote: Messenger? = null
    private var binder: IBinder? = null
    private var connection: ServiceConnection? = null
    private var pending: CancellableContinuation<JSONObject>? = null
    private var connected: CancellableContinuation<Unit>? = null
    private var request = 0
    private var generation = 0
    private var worker = 0 // Private same-UID process control only. Never diagnostics/export.
    private var releasing = false
    private var death = CompletableDeferred<Unit>()
    override val isOffline = true

    override suspend fun load(resources: ValidatedCoreResources) =
        withContext(Dispatchers.Main.immediate) {
            connect()
            val hello = call(LocalInferenceService.HELLO, JSONObject())
            worker = hello.getInt("worker")
            if (worker <= 0 || worker == Process.myPid())
                throw LocalFault(LocalFailure.INVALID_RESULT)
            val payload = JSONObject()
            this@AndroidLocalRuntime.resources.resolve(resources).forEach { (role, path) ->
                payload.put(role.name, path.absolutePath)
            }
            val result = call(LocalInferenceService.LOAD, payload)
            measured(LocalStage.MODEL_LOAD_COMPLETE, result)
        }

    override suspend fun recognize(
        reference: AudioReference,
        language: LanguageId,
    ): NativeRecognition =
        withContext(Dispatchers.Main.immediate) {
            val result =
                call(
                    LocalInferenceService.ASR,
                    JSONObject()
                        .put("audio", audio.resolve(reference).absolutePath)
                        .put("language", language.tag),
                )
            measured(LocalStage.ASR_COMPLETE, result)
            NativeRecognition(
                result.getString("text"),
                LanguageId.parse(result.getString("language")),
                result.getBoolean("complete"),
            )
        }

    override suspend fun translate(
        text: String,
        direction: TranslationDirection,
    ): NativeTranslation =
        withContext(Dispatchers.Main.immediate) {
            val result =
                call(
                    LocalInferenceService.TRANSLATE,
                    JSONObject()
                        .put("text", text)
                        .put(
                            "direction",
                            "${direction.source.baseLanguage}-${direction.target.baseLanguage}",
                        ),
                )
            measured(LocalStage.TRANSLATION_COMPLETE, result)
            NativeTranslation(result.getString("text"), direction, result.getBoolean("complete"))
        }

    private suspend fun connect() {
        if (remote != null) return
        releasing = false
        val epoch = ++generation
        death = CompletableDeferred()
        withTimeout(20_000) {
            suspendCancellableCoroutine { continuation ->
                connected = continuation
                val service =
                    object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, value: IBinder) {
                            if (epoch != generation || !continuation.isActive) return
                            binder = value
                            try {
                                value.linkToDeath(
                                    {
                                        Handler(Looper.getMainLooper()).post { disconnected(epoch) }
                                    },
                                    0,
                                )
                                remote = Messenger(value)
                                connected = null
                                if (continuation.isActive)
                                    continuation.resumeWith(Result.success(Unit))
                            } catch (_: Throwable) {
                                disconnected(epoch)
                            }
                        }

                        override fun onServiceDisconnected(name: ComponentName) {
                            disconnected(epoch)
                        }

                        override fun onBindingDied(name: ComponentName) {
                            disconnected(epoch)
                        }

                        override fun onNullBinding(name: ComponentName) {
                            disconnected(epoch)
                        }
                    }
                connection = service
                if (
                    !app.bindService(
                        Intent(app, LocalInferenceService::class.java),
                        service,
                        Context.BIND_AUTO_CREATE,
                    )
                ) {
                    connected = null
                    continuation.resumeWith(
                        Result.failure(LocalFault(LocalFailure.RUNTIME_UNAVAILABLE))
                    )
                }
            }
        }
    }

    private fun disconnected(epoch: Int) {
        if (epoch != generation) return
        death.complete(Unit)
        remote = null
        connected?.let {
            if (it.isActive) it.resumeWith(Result.failure(LocalFault(LocalFailure.WORKER_EXIT)))
        }
        connected = null
        pending?.let {
            if (it.isActive) it.resumeWith(Result.failure(LocalFault(LocalFailure.WORKER_EXIT)))
        }
        pending = null
        if (!releasing && android.os.Build.VERSION.SDK_INT >= 30 && worker > 0) {
            val exit =
                try {
                    app.getSystemService(ActivityManager::class.java)
                        ?.getHistoricalProcessExitReasons(app.packageName, worker, 1)
                        ?.firstOrNull()
                } catch (_: Throwable) {
                    null
                }
            safeLocalDiagnostic(
                diagnostic,
                LocalDiagnostic(
                    LocalStage.FAILED,
                    failure = LocalFailure.WORKER_EXIT,
                    exitReason = exit?.reason,
                    exitStatus = exit?.status,
                ),
            )
        }
    }

    private suspend fun call(kind: Int, payload: JSONObject): JSONObject {
        val epoch = generation
        val id = ++request
        return withTimeout(180_000) {
            suspendCancellableCoroutine { continuation ->
                pending = continuation
                val receiver =
                    Messenger(
                        Handler(Looper.getMainLooper()) { message ->
                            if (
                                generation == epoch &&
                                    message.arg1 == id &&
                                    pending === continuation &&
                                    continuation.isActive
                            ) {
                                try {
                                    val result =
                                        JSONObject(message.data.getString("payload") ?: "{}")
                                    if (message.what == LocalInferenceService.EVENT) {
                                        val stage =
                                            LocalStage.entries.firstOrNull {
                                                it.name == result.optString("stage")
                                            }
                                        if (stage != null)
                                            safeLocalDiagnostic(diagnostic, LocalDiagnostic(stage))
                                    } else if (message.what == LocalInferenceService.RESULT) {
                                        pending = null
                                        if (result.has("failure")) {
                                            val code =
                                                LocalFailure.entries.firstOrNull {
                                                    it.name == result.optString("failure")
                                                } ?: LocalFailure.NATIVE_FAILED
                                            continuation.resumeWith(
                                                Result.failure(LocalFault(code))
                                            )
                                        } else continuation.resumeWith(Result.success(result))
                                    }
                                } catch (_: Throwable) {
                                    pending = null
                                    continuation.resumeWith(
                                        Result.failure(LocalFault(LocalFailure.INVALID_RESULT))
                                    )
                                }
                            }
                            true
                        }
                    )
                try {
                    (remote ?: throw LocalFault(LocalFailure.WORKER_EXIT)).send(
                        Message.obtain().apply {
                            what = kind
                            arg1 = id
                            replyTo = receiver
                            data = Bundle().apply { putString("payload", payload.toString()) }
                        }
                    )
                } catch (_: Throwable) {
                    pending = null
                    continuation.resumeWith(Result.failure(LocalFault(LocalFailure.WORKER_EXIT)))
                }
            }
        }
    }

    private fun measured(stage: LocalStage, result: JSONObject) {
        safeLocalDiagnostic(
            diagnostic,
            LocalDiagnostic(
                stage,
                milliseconds = result.optLong("milliseconds").coerceIn(0, 180_000).toDouble(),
                workerPssBytes = result.optLong("pss").coerceIn(0, 32L * 1024 * 1024 * 1024),
            ),
        )
    }

    override suspend fun release() =
        withContext(Dispatchers.Main.immediate) {
            releasing = true
            pending = null
            connected = null
            val oldBinder = binder
            val oldDeath = death
            // Even a stalled native kernel is reclaimed by the OS. Same application UID only.
            if (worker > 0 && worker != Process.myPid() && oldBinder?.isBinderAlive == true)
                Process.killProcess(worker)
            else
                try {
                    remote?.send(Message.obtain().apply { what = LocalInferenceService.TERMINATE })
                } catch (_: Throwable) {}
            connection?.let {
                try {
                    app.unbindService(it)
                } catch (_: IllegalArgumentException) {}
            }
            app.stopService(Intent(app, LocalInferenceService::class.java))
            if (oldBinder?.isBinderAlive == true) withTimeout(10_000) { oldDeath.await() }
            ++generation // Any late result/connection is stale before another owner is admitted.
            remote = null
            binder = null
            connection = null
            worker = 0
        }
}
