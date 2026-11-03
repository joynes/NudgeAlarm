package se.joynes.nudgealarm.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.core.config.ReminderConfig
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme
import kotlin.time.Duration.Companion.minutes

class QuestDetailScreensTest {
    @get:Rule val compose = createComposeRule()
    @Test fun emptyListExplainsHowToLoadQuestsAndBackWorks() {
        var backs = 0
        compose.setContent { NudgeAlarmTheme { RoutinesScreen(emptyList(), {}, { backs++ }) } }
        compose.onNodeWithText("NO QUESTS LOADED").assertIsDisplayed()
        compose.onNodeWithText("< BACK TO GAME").performClick()
        assertEquals(1, backs)
    }
    @Test fun detailsShowConfiguredSnoozeOptionsAndBackWorks() {
        var backs = 0
        compose.setContent { NudgeAlarmTheme {
            RoutinesScreen(listOf(ReminderConfig("water", "Water", "0 9 * * *", snoozeOptions = listOf(15.minutes, 60.minutes))), {}, { backs++ })
        } }
        compose.onNodeWithText("15m | 60m").assertIsDisplayed()
        compose.onNodeWithText("< BACK TO GAME").performClick()
        assertEquals(1, backs)
    }
    @Test fun runningStatusShowsLastTriggerAndBothBackControlsWork() {
        val edited = mutableListOf<String>()
        var backs = 0
        val rule = ReminderConfig("water", "Water", "0 9 * * *")
        compose.setContent { NudgeAlarmTheme {
            StatusScreen(true, null, null, "water", 1800000000000, listOf(rule), { edited.add(it) }, { backs++ })
        } }
        compose.onNodeWithText("Running").assertIsDisplayed()
        compose.onNodeWithText("Last Trigger").performTouchInput { longClick() }
        assertEquals(listOf("water"), edited)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Back").performClick()
        assertEquals(2, backs)
    }
}
