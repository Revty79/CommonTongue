package com.commontongue.prototype.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class ConversationTheme(val title: String, val description: String) {
    CLEAN_CLEAR("Clean & Clear", "Bright, simple and focused"),
    MODERN_DARK("Modern Dark", "Calm contrast for low light"),
    CONVERSATION("Conversation", "Two sides of a shared conversation"),
    TRAVEL("Travel", "Warm colours and a wider world");

    companion object {
        fun restore(id: String?) = entries.firstOrNull { it.name == id } ?: CLEAN_CLEAR
    }
}

internal fun ConversationTheme.colors() =
    when (this) {
        ConversationTheme.CLEAN_CLEAR ->
            lightColorScheme(
                primary = Color(0xFF006C68),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFC9EEEA),
                onPrimaryContainer = Color(0xFF073C3A),
                secondary = Color(0xFF84412E),
                secondaryContainer = Color(0xFFFFE1D5),
                onSecondaryContainer = Color(0xFF4D271D),
                background = Color(0xFFF6FAF9),
                onBackground = Color(0xFF172F31),
                surface = Color.White,
                onSurface = Color(0xFF172F31),
                surfaceVariant = Color(0xFFE8F0EF),
                onSurfaceVariant = Color(0xFF425859),
                outline = Color(0xFF667B7B),
            )
        ConversationTheme.MODERN_DARK ->
            darkColorScheme(
                primary = Color(0xFF83D9D1),
                onPrimary = Color(0xFF073C3A),
                primaryContainer = Color(0xFF164D4B),
                onPrimaryContainer = Color(0xFFC6EFEB),
                secondary = Color(0xFFF4B394),
                secondaryContainer = Color(0xFF54382F),
                onSecondaryContainer = Color(0xFFFFE1D5),
                background = Color(0xFF101B20),
                onBackground = Color(0xFFE3EFEE),
                surface = Color(0xFF17272D),
                onSurface = Color(0xFFE3EFEE),
                surfaceVariant = Color(0xFF273A40),
                onSurfaceVariant = Color(0xFFC0D1D2),
                outline = Color(0xFF91A5A5),
            )
        ConversationTheme.CONVERSATION ->
            lightColorScheme(
                primary = Color(0xFF00665C),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFC4EBDF),
                onPrimaryContainer = Color(0xFF123F34),
                secondary = Color(0xFF67523E),
                secondaryContainer = Color(0xFFF1E1CF),
                onSecondaryContainer = Color(0xFF493827),
                background = Color(0xFFF3F6F2),
                onBackground = Color(0xFF25362D),
                surface = Color(0xFFFCFFFA),
                onSurface = Color(0xFF25362D),
                surfaceVariant = Color(0xFFE3EBE1),
                onSurfaceVariant = Color(0xFF455A4B),
                outline = Color(0xFF6E8071),
            )
        ConversationTheme.TRAVEL ->
            lightColorScheme(
                primary = Color(0xFF00676D),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFD1EBEA),
                onPrimaryContainer = Color(0xFF123D42),
                secondary = Color(0xFF804323),
                secondaryContainer = Color(0xFFFFDEC6),
                onSecondaryContainer = Color(0xFF4B2B1B),
                background = Color(0xFFFCF5E9),
                onBackground = Color(0xFF34342E),
                surface = Color(0xFFFFFBF2),
                onSurface = Color(0xFF34342E),
                surfaceVariant = Color(0xFFEDE7D7),
                onSurfaceVariant = Color(0xFF5B594B),
                outline = Color(0xFF7D7B6A),
            )
    }
