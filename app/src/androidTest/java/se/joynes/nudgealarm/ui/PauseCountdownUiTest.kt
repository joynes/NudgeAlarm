package se.joynes.nudgealarm.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme

class PauseCountdownUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun pauseIconShowsRemainingAndEndTimeAndStillResumesOnTap() {
        val now = System.currentTimeMillis()
        var resumed = false
        rule.setContent { NudgeAlarmTheme { PauseAlertIndicator(true, now + 90 * 60_000L, now) { resumed = true } } }
        rule.onNodeWithText("1h 30m left").assertIsDisplayed()
        rule.onNodeWithTag("pause-alerts-until").assertIsDisplayed()
        rule.onNodeWithTag("pause-alerts-icon").performClick()
        rule.runOnIdle { assertTrue(resumed) }
    }

    @Test fun countdownUpdatesAndDisappearsWhenAlertsResume() {
        val initial = System.currentTimeMillis()
        val now = mutableLongStateOf(initial)
        val quiet = mutableStateOf(true)
        rule.setContent { NudgeAlarmTheme { PauseAlertIndicator(quiet.value, initial + 60_000L, now.longValue, {}) } }
        rule.onNodeWithText("1m left").assertIsDisplayed()
        rule.runOnIdle { now.longValue = initial + 1000L }
        rule.onNodeWithText("59s left").assertIsDisplayed()
        rule.runOnIdle { now.longValue = initial + 60_000L }
        rule.onNodeWithText("Resuming…").assertIsDisplayed()
        rule.runOnIdle { quiet.value = false }
        rule.onNodeWithTag("pause-alerts-remaining").assertDoesNotExist()
        rule.onNodeWithTag("pause-alerts-until").assertDoesNotExist()
    }

    @Test fun indefinitePauseDoesNotInventAnEndTime() {
        rule.setContent { NudgeAlarmTheme { PauseAlertIndicator(true, 0, System.currentTimeMillis(), {}) } }
        rule.onNodeWithText("PAUSED").assertIsDisplayed()
        rule.onNodeWithTag("pause-alerts-until").assertDoesNotExist()
    }

    @Test fun snoozedQuestShowsCountdownEvenWhenItEndsAfterToday() {
        val now = System.currentTimeMillis()
        rule.setContent { NudgeAlarmTheme { SnoozedQuestCountdown(now + (1440 + 30) * 60_000L, now) } }
        rule.onNodeWithText("Snoozed · 1d 30m left", substring = true).assertIsDisplayed()
        rule.onNodeWithText("until", substring = true).assertIsDisplayed()
    }
}
