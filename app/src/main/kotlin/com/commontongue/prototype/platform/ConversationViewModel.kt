package com.commontongue.prototype.platform

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.commontongue.prototype.ui.theme.ConversationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Retained across rotation; owns no Activity, permission launcher, or persistent conversation. */
class ConversationViewModel(application: Application) : AndroidViewModel(application) {
    private val ownedScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val graph = ConversationGraph(application, ownedScope)
    val turns = graph.turns
    private val preferences = application.getSharedPreferences("appearance", 0)
    private val selected =
        MutableStateFlow(ConversationTheme.restore(preferences.getString("theme", null)))
    val theme = selected.asStateFlow()

    fun selectTheme(value: ConversationTheme) {
        preferences.edit().putString("theme", value.name).apply()
        selected.value = value
    }

    fun diagnostics() = graph.diagnostics()

    override fun onCleared() {
        turns.close().invokeOnCompletion { ownedScope.cancel() }
    }
}
