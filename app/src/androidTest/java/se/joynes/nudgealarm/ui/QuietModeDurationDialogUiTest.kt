package se.joynes.nudgealarm.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme
import se.joynes.nudgealarm.storage.SnoozePreferences
import android.content.Context

class QuietModeDurationDialogUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun reusesSnoozeWheelsAndSavedCustomDuration() {
        composeRule.activity.getSharedPreferences("snooze_picker", Context.MODE_PRIVATE).edit().clear().commit()
        SnoozePreferences(composeRule.activity).record(97)
        var selectedMinutes: Int? = null
        composeRule.setContent {
            NudgeAlarmTheme {
                QuietModeDurationDialog(
                    onSelect = { selectedMinutes = it },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("PAUSE ALL ALERTS").assertIsDisplayed()
        composeRule.onNodeWithText("HOURS").assertIsDisplayed()
        composeRule.onNodeWithText("MINUTES").assertIsDisplayed()
        composeRule.onNodeWithText("Resume alerts in 1h 37m", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("PAUSE").performClick()

        composeRule.runOnIdle { assertEquals(97, selectedMinutes) }
    }
}
