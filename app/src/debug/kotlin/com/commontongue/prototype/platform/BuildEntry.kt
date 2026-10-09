package com.commontongue.prototype.platform

import android.app.Activity
import android.content.Intent
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

internal object BuildEntry {
    @Composable
    fun Controls(activity: Activity) {
        Button(
            onClick = { activity.startActivity(Intent(activity, LocalTrialActivity::class.java)) }
        ) {
            Text("Pass 7 internal adapter checks")
        }
    }
}
