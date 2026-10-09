package com.commontongue.local

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One serialized native owner for both capabilities; latest request cancels/reclaims the previous
 * worker.
 */
class LocalAiSession(
    private val resources: CoreResourceSource,
    private val runtime: LocalRuntime,
    private val diagnostic: (LocalDiagnostic) -> Unit = {},
) {
    private val state = Mutex()
    private val admission = Mutex()
    private val operation = Mutex()
    private var active: Job? = null
    private var loaded = false
    private var closed = false
    private var foreground = true
    private var broken = false

    internal suspend fun <T> execute(action: suspend (LocalRuntime) -> T): T = coroutineScope {
        val job = currentCoroutineContext().job
        val previous = admission.withLock {
            state.withLock {
                if (closed) throw LocalFault(LocalFailure.CLOSED)
                if (!foreground) throw LocalFault(LocalFailure.BACKGROUND)
                active.also { active = job }
            }
        }
        previous?.cancel()
        try {
            operation.withLock {
                try {
                    currentCoroutineContext().ensureActive()
                    state.withLock {
                        if (closed) throw LocalFault(LocalFailure.CLOSED)
                        if (!foreground) throw LocalFault(LocalFailure.BACKGROUND)
                    }
                    if (broken) throw LocalFault(LocalFailure.RUNTIME_UNAVAILABLE)
                    if (!runtime.isOffline) throw LocalFault(LocalFailure.OFFLINE_UNAVAILABLE)
                    if (!loaded) {
                        emit(LocalStage.RESOURCE_VALIDATE_START)
                        val snapshot = resources.validated()
                        snapshot.verifyIdentity()
                        emit(LocalStage.RESOURCE_VALIDATE_COMPLETE)
                        emit(LocalStage.MODEL_LOAD_START)
                        runtime.load(snapshot)
                        currentCoroutineContext().ensureActive()
                        loaded = true
                        emit(LocalStage.MODEL_LOAD_COMPLETE)
                    }
                    val value = action(runtime)
                    currentCoroutineContext().ensureActive()
                    value
                } catch (error: CancellationException) {
                    emit(LocalStage.CANCELLED)
                    reclaim()
                    throw error
                } catch (error: Throwable) {
                    reclaim()
                    val fault = error as? LocalFault ?: LocalFault(LocalFailure.NATIVE_FAILED)
                    safeLocalDiagnostic(
                        diagnostic,
                        LocalDiagnostic(LocalStage.FAILED, failure = fault.reason),
                    )
                    throw fault
                }
            }
        } finally {
            withContext(NonCancellable) { state.withLock { if (active === job) active = null } }
        }
    }

    private suspend fun reclaim() =
        withContext(NonCancellable) {
            loaded = false
            try {
                runtime.release()
            } catch (_: Throwable) {
                // Preserve cancellation/original failure, but never reuse an owner whose cleanup
                // failed.
                broken = true
                safeLocalDiagnostic(
                    diagnostic,
                    LocalDiagnostic(LocalStage.FAILED, failure = LocalFailure.NATIVE_FAILED),
                )
            }
        }

    private fun emit(stage: LocalStage) = safeLocalDiagnostic(diagnostic, LocalDiagnostic(stage))

    suspend fun prepare() {
        execute {}
    }

    suspend fun cancel() =
        withContext(NonCancellable) {
            admission.withLock {
                val job = state.withLock { active }
                job?.cancelAndJoin()
                operation.withLock { reclaim() }
            }
        }

    suspend fun setForeground(value: Boolean) {
        state.withLock { foreground = value }
        if (!value) cancel()
    }

    suspend fun release() {
        cancel()
        emit(LocalStage.RELEASED)
    }

    suspend fun close() =
        withContext(NonCancellable) {
            state.withLock { closed = true }
            cancel()
        }
}
