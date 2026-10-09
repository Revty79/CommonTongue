package com.commontongue.prototype.ui.foundation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.commontongue.prototype.platform.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun singleLauncherDisplaysRealTranslatorWithAccessibleTalkControls() {
        compose.onNodeWithText("Common Tongue").assertIsDisplayed()
        compose
            .onNodeWithContentDescription("Hold to speak English")
            .assertIsDisplayed()
            .assertIsNotEnabled()
        compose
            .onNodeWithContentDescription("Mantén presionado para hablar español")
            .assertIsDisplayed()
            .assertIsNotEnabled()
        compose.onNodeWithText("Offline language resources are not installed").assertIsDisplayed()
    }

    @Test
    fun themeChoicePersistsWithoutAnotherLauncherOrConversationReset() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.onNodeWithText("Travel").performScrollTo().performClick()
        compose.onNodeWithText("Done").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Travel · Selected").performScrollTo().assertIsDisplayed()
        val context = compose.activity
        org.junit.Assert.assertEquals(
            "TRAVEL",
            context.getSharedPreferences("appearance", 0).getString("theme", null),
        )
        compose.onNodeWithText("Clean & Clear").performScrollTo().performClick()
        compose.onNodeWithText("Done").performClick()
    }
}
