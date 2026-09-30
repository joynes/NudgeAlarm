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

class SnoozePickerDialogUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun quickChoiceReturnsDurationAndExactTimeIsAvailable() {
        var selectedMinutes: Int? = null
        composeRule.setContent {
            NudgeAlarmTheme {
                SnoozePickerDialog(
                    title = "SNOOZE QUEST",
                    description = "When should this nag again?",
                    onSnooze = { selectedMinutes = it },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("SNOOZE QUEST").assertIsDisplayed()
        composeRule.onNodeWithText("PICK DATE & TIME").assertIsDisplayed()
        composeRule.onNodeWithText("30M").performClick()
        composeRule.runOnIdle { assertEquals(30, selectedMinutes) }
    }
}
