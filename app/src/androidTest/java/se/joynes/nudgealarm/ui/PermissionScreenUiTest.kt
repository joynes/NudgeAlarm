package se.joynes.nudgealarm.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import org.junit.Assert.assertEquals

class PermissionScreenUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun continuingLocationDisclosureOpensThisAppsSettingsAndBackReturnsToTheGame() {
        val opened = mutableListOf<Intent>()
        var backs = 0
        val context = object : ContextWrapper(composeRule.activity) {
            override fun startActivity(intent: Intent) { opened.add(intent) }
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                PermissionScreen({}, { backs++ })
            }
        }
        composeRule.onNodeWithTag("backgroundLocationUnlock").performClick()
        composeRule.onNodeWithText("NOT NOW").performClick()
        assertEquals(0, opened.size)
        composeRule.onNodeWithTag("backgroundLocationUnlock").performClick()
        composeRule.onNodeWithText("CONTINUE TO SETTINGS").performClick()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, opened.single().action)
        assertEquals("package:${context.packageName}", opened.single().data.toString())
        composeRule.onNodeWithText("< BACK TO GAME").performClick()
        assertEquals(1, backs)
    }

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
