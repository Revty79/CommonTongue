package com.commontongue.prototype.platform

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import com.commontongue.prototype.ui.foundation.FoundationRoute
import com.commontongue.prototype.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Column {
                    BuildEntry.Controls(this@MainActivity)
                    FoundationRoute()
                }
            }
        }
    }
}
