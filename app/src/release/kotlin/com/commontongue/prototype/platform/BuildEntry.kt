package com.commontongue.prototype.platform

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable

internal object BuildEntry {
    @Composable fun Controls(activity: Activity) = Unit

    @Composable fun Export(activity: ComponentActivity, results: () -> String) = Unit
}
