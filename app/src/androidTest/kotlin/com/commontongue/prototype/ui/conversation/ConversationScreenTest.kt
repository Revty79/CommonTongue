package com.commontongue.prototype.ui.conversation

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.commontongue.prototype.platform.MainActivity
import com.commontongue.prototype.ui.theme.ConversationAppearance
import com.commontongue.prototype.ui.theme.ConversationTheme
import com.commontongue.translation.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun screen(
        theme: ConversationTheme,
        press: (TalkSide) -> Long?,
        release: (Long) -> Unit,
        cancel: (Long) -> Unit,
    ) {
        compose.runOnUiThread {
            compose.activity.setContent {
                ConversationAppearance(theme) {
                    ConversationScreen(
                        ConversationState(
                            modelsReady = true,
                            side = TalkSide.ENGLISH,
                            recognized = "Actual source",
                            translated = "Actual translation",
                            replayAvailable = true,
                        ),
                        theme,
                        {},
                        press,
                        release,
                        cancel,
                        {},
                        {},
                        {},
                        {},
                        {},
                    )
                }
            }
        }
    }

    @Test
    fun bothLargeControlsDispatchPressAndReleaseInAllFourSkins() {
        ConversationTheme.entries.forEach { theme ->
            val sides = mutableListOf<TalkSide>()
            val released = mutableListOf<Long>()
            val cancelled = mutableListOf<Long>()
            screen(
                theme,
                {
                    sides += it
                    71L
                },
                released::add,
                cancelled::add,
            )
            compose
                .onNodeWithContentDescription("Hold to speak English")
                .performScrollTo()
                .performTouchInput {
                    down(center)
                    advanceEventTime(300)
                    up()
                }
            compose
                .onNodeWithContentDescription("Mantén presionado para hablar español")
                .performScrollTo()
                .performTouchInput {
                    down(center)
                    advanceEventTime(300)
                    up()
                }
            compose.runOnIdle {
                assertEquals(listOf(TalkSide.ENGLISH, TalkSide.SPANISH), sides)
                assertEquals(listOf(71L, 71L), released)
                assertTrue(cancelled.isEmpty())
            }
            compose.onNodeWithText("Actual translation").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Replay translation").performScrollTo().assertIsEnabled()
        }
    }

    @Test
    fun aCancelledGestureCancelsItsTurnInsteadOfSendingAudio() {
        val cancelled = mutableListOf<Long>()
        val released = mutableListOf<Long>()
        screen(ConversationTheme.CLEAN_CLEAR, { 12L }, released::add, cancelled::add)
        compose
            .onNodeWithContentDescription("Hold to speak English")
            .performScrollTo()
            .performTouchInput {
                down(center)
                cancel()
            }
        compose.runOnIdle {
            assertEquals(listOf(12L), cancelled)
            assertTrue(released.isEmpty())
        }
    }
}
