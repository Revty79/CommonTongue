package com.commontongue.prototype.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class ConversationThemeTest {
    @Test
    fun fourApprovedIdsRoundTripAndUnknownDefaultsToClean() {
        assertEquals(
            listOf("CLEAN_CLEAR", "MODERN_DARK", "CONVERSATION", "TRAVEL"),
            ConversationTheme.entries.map { it.name },
        )
        ConversationTheme.entries.forEach { assertEquals(it, ConversationTheme.restore(it.name)) }
        assertEquals(ConversationTheme.CLEAN_CLEAR, ConversationTheme.restore("old-theme"))
        assertEquals(ConversationTheme.CLEAN_CLEAR, ConversationTheme.restore(null))
    }

    @Test
    fun everyPrimaryTextSurfaceMeetsNormalTextContrast() {
        ConversationTheme.entries.forEach { theme ->
            val c = theme.colors()
            listOf(
                    c.background to c.onBackground,
                    c.surface to c.onSurface,
                    c.surfaceVariant to c.onSurfaceVariant,
                    c.primary to c.onPrimary,
                    c.primaryContainer to c.onPrimaryContainer,
                    c.secondaryContainer to c.onSecondaryContainer,
                )
                .forEach { (background, text) ->
                    assertTrue(
                        "${theme.name}: insufficient contrast",
                        contrast(background, text) >= 4.5,
                    )
                }
        }
    }

    private fun luminance(c: Color): Double {
        fun linear(v: Float): Double =
            if (v <= .04045f) v / 12.92 else Math.pow((v + .055) / 1.055, 2.4)
        return .2126 * linear(c.red) + .7152 * linear(c.green) + .0722 * linear(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val first = luminance(a)
        val second = luminance(b)
        return (maxOf(first, second) + .05) / (minOf(first, second) + .05)
    }
}
