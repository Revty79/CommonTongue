package com.commontongue.prototype.platform

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.commontongue.prototype.ui.conversation.ConversationScreen
import com.commontongue.prototype.ui.theme.ConversationAppearance
import com.commontongue.prototype.ui.theme.ConversationTheme
import com.commontongue.translation.TalkSide
import com.commontongue.translation.TurnStage

class MainActivity : ComponentActivity() {
    private val model: ConversationViewModel by viewModels()
    private val permission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted)
                model.turns.permissionDenied(
                    !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                )
            else
                Toast.makeText(
                        this,
                        "Microphone is ready. Hold a talk button again.",
                        Toast.LENGTH_LONG,
                    )
                    .show()
            // A permission dialog ends the original press. Never start recording without a new
            // gesture.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by model.theme.collectAsStateWithLifecycle()
            val state by model.turns.state.collectAsStateWithLifecycle()
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = theme != ConversationTheme.MODERN_DARK
                    isAppearanceLightNavigationBars = theme != ConversationTheme.MODERN_DARK
                }
            }
            val busy =
                state.stage in
                    setOf(
                        TurnStage.LOADING_MODELS,
                        TurnStage.STARTING,
                        TurnStage.LISTENING,
                        TurnStage.RECOGNIZING,
                        TurnStage.TRANSLATING,
                        TurnStage.SYNTHESIZING,
                        TurnStage.SPEAKING,
                    )
            DisposableEffect(busy) {
                if (busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
            ConversationAppearance(theme) {
                ConversationScreen(
                    state,
                    theme,
                    model::selectTheme,
                    ::press,
                    model.turns::release,
                    model.turns::cancelPress,
                    model.turns::replay,
                    { model.turns.cancel() },
                    { model.turns.cancel(clear = true) },
                    model.turns::retry,
                    {
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:$packageName"),
                            )
                        )
                    },
                    { BuildEntry.Controls(this@MainActivity) },
                    { BuildEntry.Export(this@MainActivity, model::diagnostics) },
                )
            }
        }
    }

    private fun press(side: TalkSide): Long? {
        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.RECORD_AUDIO)
            return null
        }
        return model.turns.press(side)
    }

    override fun onStart() {
        super.onStart()
        model.turns.foreground(true)
    }

    override fun onStop() {
        model.turns.foreground(false)
        super.onStop()
    }
}
