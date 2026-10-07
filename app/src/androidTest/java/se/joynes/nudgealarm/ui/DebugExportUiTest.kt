package se.joynes.nudgealarm.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme

class DebugExportUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun exportIsAvailableEvenWhenTheLogIsEmpty() {
        var exports = 0
        composeRule.setContent { NudgeAlarmTheme {
            EventLogScreen(emptyList(), emptySet(), {}, {}, {}, onExport = { exports++ })
        } }
        composeRule.onNodeWithText("EXPORT DEBUG REPORT").assertIsDisplayed().performClick()
        assertEquals(1, exports)
        composeRule.onNodeWithText("LOG EMPTY").assertIsDisplayed()
    }
}
