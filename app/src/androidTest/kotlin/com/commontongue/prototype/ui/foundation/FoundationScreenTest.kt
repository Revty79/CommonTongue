package com.commontongue.prototype.ui.foundation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.commontongue.prototype.platform.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchedActivityDisplaysFoundationContent() {
        compose.onNodeWithText("Common Tongue").assertIsDisplayed()
        compose.onNodeWithText("Foundation Build").assertIsDisplayed()
        compose.onNodeWithText("Translation engine not installed yet.").assertIsDisplayed()
        compose.onNodeWithText("Offline-first mobile translation prototype.").assertIsDisplayed()
    }
}
