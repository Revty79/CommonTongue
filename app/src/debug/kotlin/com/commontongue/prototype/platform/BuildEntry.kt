package com.commontongue.prototype.platform

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState

internal object BuildEntry {
    @Composable
    fun Controls(activity: Activity) {
        Button(
            onClick = { activity.startActivity(Intent(activity, LocalTrialActivity::class.java)) }
        ) {
            Text("Internal test tools / resource setup")
        }
    }

    @Composable
    fun Export(activity: ComponentActivity, results: () -> String) {
        val current = rememberUpdatedState(results)
        val launcher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                if (uri != null) {
                    try {
                        activity.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                            it.write(current.value())
                        }
                        android.widget.Toast.makeText(
                                activity,
                                "Diagnostics exported. No speech or text was included.",
                                android.widget.Toast.LENGTH_LONG,
                            )
                            .show()
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(
                                activity,
                                "Results could not be saved. Try again.",
                                android.widget.Toast.LENGTH_LONG,
                            )
                            .show()
                    }
                }
            }
        Button(onClick = { launcher.launch("CommonTongue-Pass8-Results.json") }) {
            Text("Export internal diagnostics")
        }
    }
}
