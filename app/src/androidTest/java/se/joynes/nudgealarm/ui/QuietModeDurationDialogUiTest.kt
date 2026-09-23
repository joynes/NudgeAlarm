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

class QuietModeDurationDialogUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun offersAllDurationsAndReturnsSelection() {
        var selectedMinutes: Int? = null
        composeRule.setContent {
            NudgeAlarmTheme {
                QuietModeDurationDialog(
                    onSelect = { selectedMinutes = it },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("QUIET MODE").assertIsDisplayed()
        listOf("30M", "1H", "2H", "4H", "24H").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        composeRule.onNodeWithText("2H").performClick()

        composeRule.runOnIdle { assertEquals(120, selectedMinutes) }
    }
}
