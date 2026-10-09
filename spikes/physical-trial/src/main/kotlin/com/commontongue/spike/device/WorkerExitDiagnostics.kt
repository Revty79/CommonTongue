package com.commontongue.spike.device

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import org.json.JSONArray
import org.json.JSONObject

/** Own-app exit metadata only. Never read/export descriptions, traces, PIDs, UIDs or raw names. */
internal object WorkerExitDiagnostics {
    fun isAlive(pid: Int): Boolean? {
        if (pid <= 0) return null
        return try {
            Os.kill(pid, 0)
            true
        } catch (error: ErrnoException) {
            if (error.errno == OsConstants.ESRCH) false else null
        }
    }

    private val reasons =
        listOf(
            "UNKNOWN",
            "EXIT_SELF",
            "SIGNALED",
            "LOW_MEMORY",
            "CRASH",
            "CRASH_NATIVE",
            "ANR",
            "INITIALIZATION_FAILURE",
            "PERMISSION_CHANGE",
            "EXCESSIVE_RESOURCE_USAGE",
            "USER_REQUESTED",
            "USER_STOPPED",
            "DEPENDENCY_DIED",
            "OTHER",
            "FREEZER",
            "PACKAGE_STATE_CHANGE",
            "PACKAGE_UPDATED",
        )

    fun lookup(context: Context, pid: Int, started: Long, runId: String): JSONObject {
        if (Build.VERSION.SDK_INT < 30) return JSONObject().put("availability", "UNSUPPORTED")
        if (pid <= 0) return JSONObject().put("availability", "MISSING_WORKER_IDENTITY")
        return try {
            val manager = context.getSystemService(ActivityManager::class.java)
            val now = System.currentTimeMillis()
            val info =
                manager.getHistoricalProcessExitReasons(context.packageName, pid, 8).firstOrNull {
                    ExitMatch.belongs(
                        it.pid,
                        pid,
                        it.processName,
                        context.packageName + ":inference",
                        it.timestamp,
                        started,
                        now,
                    )
                } ?: return JSONObject().put("availability", "NOT_YET_AVAILABLE")
            safe(info, now)
                .put("availability", "AVAILABLE")
                .put("correlation", "MATCHED_WORKER")
                .apply {
                    val checkpoint =
                        info.processStateSummary?.let {
                            StageCheckpoint.parse(it.toString(Charsets.US_ASCII))
                        }
                    if (checkpoint?.runId == runId)
                        put("process_checkpoint", JSONObject(checkpoint.exportFields()))
                }
        } catch (_: Exception) {
            JSONObject().put("availability", "QUERY_FAILED")
        }
    }

    fun history(context: Context): JSONObject {
        if (Build.VERSION.SDK_INT < 30) return JSONObject().put("availability", "UNSUPPORTED")
        return try {
            val now = System.currentTimeMillis()
            val manager = context.getSystemService(ActivityManager::class.java)
            val entries =
                manager
                    .getHistoricalProcessExitReasons(context.packageName, 0, 32)
                    .filter { it.processName == context.packageName + ":inference" }
                    .take(8)
                    .map { safe(it, now).put("correlation", "UNASSIGNED_HISTORY") }
            JSONObject().put("availability", "AVAILABLE").put("entries", JSONArray(entries))
        } catch (_: Exception) {
            JSONObject().put("availability", "QUERY_FAILED")
        }
    }

    @android.annotation.TargetApi(30)
    private fun safe(info: ApplicationExitInfo, now: Long): JSONObject =
        JSONObject()
            .put("reason_code", info.reason)
            .put("reason", reasons.getOrNull(info.reason) ?: "UNKNOWN_REASON")
            .put("status", info.status)
            .put("importance", info.importance)
            .put("pss_kb", info.pss)
            .put("rss_kb", info.rss)
            .put("age_ms", (now - info.timestamp).coerceAtLeast(0))
            .put(
                "low_memory_kill_report_supported",
                ActivityManager.isLowMemoryKillReportSupported(),
            )
            .apply {
                if (
                    info.reason == ApplicationExitInfo.REASON_SIGNALED ||
                        info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE
                ) {
                    val name =
                        mapOf(
                            4 to "SIGILL",
                            6 to "SIGABRT",
                            7 to "SIGBUS",
                            9 to "SIGKILL",
                            11 to "SIGSEGV",
                            15 to "SIGTERM",
                        )[info.status]
                    if (name != null) put("signal", name)
                }
            }
}
