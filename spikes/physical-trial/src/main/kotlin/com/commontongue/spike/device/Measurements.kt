package com.commontongue.spike.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import java.io.File
import org.json.JSONObject

internal object Measurements {
    fun now(): Long = SystemClock.elapsedRealtimeNanos()

    fun milliseconds(start: Long): Double = (now() - start) / 1e6

    fun offline(context: Context): JSONObject {
        fun setting(name: String): Int =
            try {
                Settings.Global.getInt(context.contentResolver, name, -1)
            } catch (_: Exception) {
                -1
            }
        return JSONObject()
            .put(
                "airplane_mode_on",
                setting(Settings.Global.AIRPLANE_MODE_ON) == 1,
            )
            .put(
                "wifi_setting_off",
                setting(Settings.Global.WIFI_ON) == 0,
            )
            .put(
                "mobile_data_setting_off",
                !context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) ||
                    setting("mobile_data") == 0,
            )
            .put("internet_permission", false)
    }

    fun offlineProofReady(context: Context): Boolean {
        val state = offline(context)
        return (state.getBoolean("airplane_mode_on") &&
            state.getBoolean("wifi_setting_off") &&
            state.getBoolean("mobile_data_setting_off"))
    }

    fun snapshot(context: Context): JSONObject {
        val activity = context.getSystemService(ActivityManager::class.java)
        val system = ActivityManager.MemoryInfo().also { activity.getMemoryInfo(it) }
        val process = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        fun statusKb(name: String): Long? =
            try {
                File("/proc/self/status").useLines { lines ->
                    lines
                        .firstOrNull { it.startsWith("$name:") }
                        ?.trim()
                        ?.split(Regex("\\s+"))
                        ?.getOrNull(1)
                        ?.toLongOrNull()
                }
            } catch (_: Exception) {
                null
            }
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val temperature = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val batteryStatus = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return JSONObject()
            .put("elapsed_ms", SystemClock.elapsedRealtime())
            .put("process_pss_kb", process.totalPss)
            .put("process_rss_kb", statusKb("VmRSS") ?: JSONObject.NULL)
            .put("process_hwm_rss_kb", statusKb("VmHWM") ?: JSONObject.NULL)
            .put("native_heap_bytes", Debug.getNativeHeapAllocatedSize())
            .put("system_available_ram_bytes", system.availMem)
            .put("system_total_ram_bytes", system.totalMem)
            .put("system_low_memory", system.lowMemory)
            .put("process_cpu_ms", Process.getElapsedCpuTime())
            .put(
                "battery_percent",
                if (level >= 0 && scale > 0) level * 100.0 / scale else JSONObject.NULL,
            )
            .put(
                "battery_temperature_c",
                if (temperature != null && temperature != Int.MIN_VALUE) temperature / 10.0
                else JSONObject.NULL,
            )
            .put(
                "power_source",
                when (plugged) {
                    0 -> "NONE"
                    1 -> "AC"
                    2 -> "USB"
                    4 -> "WIRELESS"
                    else -> "UNKNOWN"
                },
            )
            .put(
                "battery_charging",
                if (batteryStatus != null && batteryStatus >= 0)
                    batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING
                else JSONObject.NULL,
            )
            .put(
                "thermal_status",
                if (Build.VERSION.SDK_INT >= 29)
                    context.getSystemService(PowerManager::class.java).currentThermalStatus
                else JSONObject.NULL,
            )
            .put("execution_abi", if (Process.is64Bit()) "arm64-v8a" else "32-bit")
    }
}
