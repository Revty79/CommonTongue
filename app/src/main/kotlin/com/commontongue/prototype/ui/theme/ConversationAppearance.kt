package com.commontongue.prototype.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun ConversationAppearance(theme: ConversationTheme, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = theme.colors(), content = content)
}
