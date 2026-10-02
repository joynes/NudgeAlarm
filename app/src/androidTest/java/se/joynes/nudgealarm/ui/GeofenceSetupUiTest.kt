package se.joynes.nudgealarm.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme

class GeofenceSetupUiTest {
    @get:Rule val composeRule = createComposeRule()
    @Test fun setupExplainsPermissionsAndOpensTheirScreen() {
        var opened = false
        composeRule.setContent { NudgeAlarmTheme { GeofenceSetupCard { opened = true } } }
        composeRule.onNodeWithText("PLACE-ONLY REMINDERS").assertIsDisplayed()
        composeRule.onNodeWithText("3. In Android permissions", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("geofence-status").assertIsDisplayed()
        composeRule.onNodeWithTag("geofence-permissions").performClick()
        composeRule.runOnIdle { assertTrue(opened) }
    }
}
