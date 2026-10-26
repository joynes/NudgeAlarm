package se.joynes.nudgealarm.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme

class InteractionGraceSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun defaultCanBeChangedAndDisabledWithOneTap() {
        var selected = 15
        compose.setContent { NudgeAlarmTheme {
            var minutes by remember { mutableIntStateOf(15) }
            InteractionGraceSettings(minutes) { minutes = it; selected = it }
        } }
        compose.onNodeWithTag("interaction-grace-15").assertIsSelected()
        compose.onNodeWithTag("interaction-grace-30").performClick().assertIsSelected()
        assertEquals(30, selected)
        compose.onNodeWithTag("interaction-grace-0").performClick().assertIsSelected()
        compose.onNodeWithText("No automatic break").assertIsDisplayed()
        assertEquals(0, selected)
    }

    @Test fun customDurationUsesCurrentValueAndCancelKeepsIt() {
        var selected = 47
        compose.setContent { NudgeAlarmTheme { InteractionGraceSettings(selected) { selected = it } } }
        compose.onNodeWithText("Custom…").performClick()
        compose.onNodeWithTag("grace-hours").assertExists()
        compose.onNodeWithTag("grace-minutes").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(47, selected)
        compose.onNodeWithText("Custom…").performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(47, selected)
    }
}
