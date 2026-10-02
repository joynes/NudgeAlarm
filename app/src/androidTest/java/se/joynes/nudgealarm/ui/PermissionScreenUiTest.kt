package se.joynes.nudgealarm.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class PermissionScreenUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun locationUnlockShowsBackgroundUseDisclosureBeforeSettings() {
        composeRule.setContent {
            PermissionScreen(
                onRequestNotificationPermission = {},
                onBack = {}
            )
        }

        composeRule.onNodeWithTag("backgroundLocationUnlock").performClick()

        composeRule.onNodeWithText("BACKGROUND LOCATION").assertIsDisplayed()
        composeRule.onNodeWithText(
            "NudgeAlarm accesses your location while the app is closed or not in use " +
                "only to decide whether a place-only quest may show its reminder. " +
                "Android and Google Play services detect entry and exit without repeated GPS requests by NudgeAlarm. " +
                "Enable precise Location and Allow all the time. NudgeAlarm does not upload your saved places."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("CONTINUE TO SETTINGS").assertIsDisplayed()
        composeRule.onNodeWithText("NOT NOW").assertIsDisplayed()
    }
}
