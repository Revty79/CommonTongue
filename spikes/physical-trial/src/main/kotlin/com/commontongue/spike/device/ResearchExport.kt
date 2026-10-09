package com.commontongue.spike.device

import android.content.Context
import android.os.Build
import android.system.Os
import android.system.OsConstants
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** No names, serials, network addresses, saved human text, recordings or raw dumps. */
internal object ResearchExport {
    fun saveInventory(context: Context) {
        val sample = Measurements.snapshot(context)
        val profile =
            JSONObject()
                .put("schema_version", 1)
                .put("device_label", "device-high-01")
                .put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL)
                .put("android_version", Build.VERSION.RELEASE)
                .put("api", Build.VERSION.SDK_INT)
                .put("supported_abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
                .put("actual_execution_abi", JSONObject.NULL)
                .put("total_ram_bytes", sample.getLong("system_total_ram_bytes"))
                .put("available_ram_bytes", sample.getLong("system_available_ram_bytes"))
                .put("cpu_core_count", Runtime.getRuntime().availableProcessors())
                .put(
                    "soc_manufacturer",
                    if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER else JSONObject.NULL,
                )
                .put(
                    "soc_model",
                    if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else JSONObject.NULL,
                )
                .put("free_storage_bytes", context.filesDir.usableSpace)
                .put("page_size_bytes", Os.sysconf(OsConstants._SC_PAGESIZE))
                .put(
                    "screen_size",
                    JSONArray(
                        listOf(
                            "${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels}"
                        )
                    ),
                )
                .put("device_type", "tablet_or_phone_unconfirmed")
                .put(
                    "reported_acceleration_features",
                    JSONArray(
                        context.packageManager.systemAvailableFeatures
                            .orEmpty()
                            .mapNotNull { it.name }
                            .filter {
                                it.matches(
                                    Regex(
                                        "android\\.hardware\\.(vulkan\\.(level|version|compute)|opengles\\.aep|neuralnetworks)"
                                    )
                                )
                            }
                    ),
                )
                .put("offline", Measurements.offline(context))
                .put("result", "NOT_TESTED")
        for (key in
            listOf(
                "battery_percent",
                "battery_temperature_c",
                "thermal_status",
                "power_source",
                "battery_charging",
            )) profile.put(key, sample.opt(key))
        File(context.filesDir, "inventory-before-load.json").writeText(profile.toString(2))
    }

    fun results(context: Context): JSONObject {
        val events = JSONArray()
        val metrics = File(context.filesDir, "metrics/session.jsonl")
        if (metrics.isFile)
            metrics.useLines { lines ->
                lines
                    .filter { it.isNotBlank() }
                    .forEach { line ->
                        val item = JSONObject(line)
                        if (item.getString("event") == "error")
                            item.put(
                                "data",
                                JSONObject()
                                    .put(
                                        "stage",
                                        item.getJSONObject("data").optString("stage", "UX"),
                                    )
                                    .put("diagnostic", "redacted")
                                    .put(
                                        "diagnostic_code",
                                        item.getJSONObject("data").optString("diagnostic_code"),
                                    )
                                    .put(
                                        "error_type",
                                        item.getJSONObject("data").optString("error_type"),
                                    )
                                    .apply {
                                        val original = item.getJSONObject("data")
                                        NativeLinkDiagnostic.validate(
                                                original.optString("native_missing_library"),
                                                original.optString("native_missing_symbol"),
                                            )
                                            .forEach { (key, value) -> put(key, value) }
                                    },
                            )
                        events.put(item)
                    }
            }
        val profile = JSONObject(File(context.filesDir, "inventory-before-load.json").readText())
        if (
            (0 until events.length()).any {
                events.getJSONObject(it).let { row ->
                    row.optString("event") == "worker_sample" &&
                        row.getJSONObject("data").optString("execution_abi") == "arm64-v8a"
                }
            }
        )
            profile.put("actual_execution_abi", "arm64-v8a")
        val fixtures = JSONArray()
        File(context.filesDir, "results")
            .listFiles()
            ?.filter { it.name.matches(Regex("fixtures-(madlad|opus)-(base|tiny)\\.json")) }
            ?.forEach {
                fixtures.put(
                    JSONObject()
                        .put("configuration", it.name.removeSuffix(".json"))
                        .put("result", JSONObject(it.readText()))
                )
            }
        return JSONObject()
            .put("schema_version", 1)
            .put(
                "app_version",
                context.packageManager.getPackageInfo(context.packageName, 0).versionName,
            )
            .put("scope", "on_device_research_export_requires_coordinator_review")
            .put("current_pack_state", ResearchFiles.diagnostics(context, "madlad", "base"))
            .put("last_pipeline_checkpoint", safeFile(context, "pipeline-diagnostics.json"))
            .put("last_worker_exit", safeFile(context, "worker-exit-diagnostics.json"))
            .put("last_tts_diagnostic", safeFile(context, "tts-diagnostics.json"))
            .put("tts_engine_init", safeFile(context, "tts-engine-diagnostics.json"))
            .put("last_tts_failure", safeFile(context, "tts-failure-diagnostics.json"))
            .put("tts_checks", safeFile(context, "tts-checks.json"))
            .put("worker_exit_history", WorkerExitDiagnostics.history(context))
            .put(
                "component_checks",
                JSONArray(
                    DiagnosticMode.entries.mapNotNull {
                        try {
                            JSONObject(
                                File(context.filesDir, "diagnostics/check-${it.name}.json")
                                    .readText()
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                ),
            )
            .put(
                "last_model_load",
                try {
                    JSONObject(File(context.filesDir, "load-diagnostics.json").readText())
                } catch (_: Exception) {
                    JSONObject().put("state", "NOT_ATTEMPTED")
                },
            )
            .put("profile_before_load", profile)
            .put("events", events)
            .put("synthetic_fixture_results", fixtures)
            .put("human_recordings_included", false)
            .put("saved_human_text_included", false)
    }

    private fun safeFile(context: Context, name: String): JSONObject =
        try {
            JSONObject(File(context.filesDir, name).readText())
        } catch (_: Exception) {
            JSONObject()
        }
}
