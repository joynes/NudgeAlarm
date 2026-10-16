package se.joynes.nudgealarm.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme
import se.joynes.nudgealarm.storage.SnoozePreferences
import android.content.Context
import java.time.ZonedDateTime
import java.time.ZoneId

class SnoozePickerDialogUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before fun clearHistory() {
        composeRule.activity.getSharedPreferences("snooze_picker", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun savedClockIsAvailableDirectlyInDurationQuickPicksAndAppliesWithOneTap() {
        val prefs = SnoozePreferences(composeRule.activity)
        prefs.saveClockShortcut("lunch", "Lunch", 12, 0)
        val now = ZonedDateTime.of(2026, 10, 16, 10, 30, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
        var selected = 0
        composeRule.setContent { NudgeAlarmTheme {
            SnoozePickerDialog("SNOOZE QUEST", "Pause this quest", { selected = it }, {}, nowMillis = { now })
        } }
        composeRule.onNodeWithTag("quick_clock_lunch").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Until Lunch, 12:00, Today").assertExists()
        composeRule.onNodeWithTag("quick_pick_5").assertExists()
        composeRule.onNodeWithTag("quick_pick_1440").assertExists()
        composeRule.onNodeWithTag("quick_clock_lunch").performClick()
        composeRule.runOnIdle {
            assertEquals(90, selected)
            assertTrue(prefs.read().recent.isEmpty())
        }
    }

    @Test fun quickClockWorksForPauseAllAndShowsTomorrowWhenTimeHasPassed() {
        SnoozePreferences(composeRule.activity).saveClockShortcut("work", "After work", 17, 0)
        val now = ZonedDateTime.of(2026, 10, 16, 18, 0, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
        var selected = 0
        composeRule.setContent { NudgeAlarmTheme {
            SnoozePickerDialog("PAUSE ALL ALERTS", "Pause all alerts", { selected = it }, {}, confirmLabel = "PAUSE", nowMillis = { now })
        } }
        composeRule.onNodeWithTag("quick_clock_work").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Until After work, 17:00, Tomorrow").assertExists()
        composeRule.onNodeWithTag("quick_clock_work").performClick()
        composeRule.runOnIdle { assertEquals(23 * 60, selected) }
    }

    @Test fun namedClockShortcutAppliesImmediatelyWithoutPollutingDurationHistory() {
        val prefs = SnoozePreferences(composeRule.activity)
        prefs.saveClockShortcut("lunch", "Lunch", 12, 0)
        val now = ZonedDateTime.of(2026, 10, 12, 10, 30, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
        var selected = 0
        composeRule.setContent { NudgeAlarmTheme {
            SnoozePickerDialog("SNOOZE QUEST", "Pause this quest", { selected = it }, {}, nowMillis = { now })
        } }
        composeRule.onNodeWithTag("snooze_clock_mode").performClick()
        composeRule.onNodeWithText("12:00 · Today").assertIsDisplayed()
        composeRule.onNodeWithTag("clock_shortcut_lunch").performClick()
        composeRule.runOnIdle {
            assertEquals(90, selected)
            assertEquals(emptyList<Int>(), prefs.read().recent)
        }
    }

    @Test fun passedClockShortcutShowsTomorrowAndAlsoWorksForPauseAll() {
        SnoozePreferences(composeRule.activity).saveClockShortcut("work", "After work", 17, 0)
        val now = ZonedDateTime.of(2026, 10, 12, 18, 0, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
        var selected = 0
        composeRule.setContent { NudgeAlarmTheme {
            SnoozePickerDialog("PAUSE ALL ALERTS", "Pause all alerts", { selected = it }, {}, confirmLabel = "PAUSE", nowMillis = { now })
        } }
        composeRule.onNodeWithTag("snooze_clock_mode").performClick()
        composeRule.onNodeWithText("17:00 · Tomorrow").assertIsDisplayed()
        composeRule.onNodeWithTag("clock_shortcut_work").performClick()
        composeRule.runOnIdle { assertEquals(23 * 60, selected) }
    }

    @Test fun shortcutsCanBeCreatedEditedAndDeletedWithoutSnoozing() {
        var selected = 0
        composeRule.setContent { NudgeAlarmTheme {
            SnoozePickerDialog("SNOOZE QUEST", "Pause this quest", { selected = it }, {})
        } }
        composeRule.onNodeWithTag("snooze_clock_mode").performClick()
        composeRule.onNodeWithText("+ ADD SHORTCUT").performScrollTo().performClick()
        composeRule.onNodeWithText("SAVE SHORTCUT").assertIsNotEnabled()
        composeRule.onNodeWithTag("clock_shortcut_name").performTextReplacement("Lunch")
        composeRule.onNodeWithText("SAVE SHORTCUT").performClick()
        var id = ""
        composeRule.runOnIdle { id = SnoozePreferences(composeRule.activity).clockShortcuts().single().id }
        composeRule.onNodeWithTag("edit_clock_shortcut_$id").performScrollTo().performClick()
        composeRule.onNodeWithTag("clock_shortcut_name").performTextReplacement("Dinner")
        composeRule.onNodeWithText("SAVE SHORTCUT").performClick()
        composeRule.runOnIdle {
            assertEquals("Dinner", SnoozePreferences(composeRule.activity).clockShortcuts().single().name)
            assertEquals(id, SnoozePreferences(composeRule.activity).clockShortcuts().single().id)
        }
        composeRule.onNodeWithTag("edit_clock_shortcut_$id").performScrollTo().performClick()
        composeRule.onNodeWithText("DELETE SHORTCUT").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(0, selected)
            assertEquals(emptyList<Any>(), SnoozePreferences(composeRule.activity).clockShortcuts())
        }
    }

    @Test fun nineQuickPicksAreArrangedInThreeAscendingRows() {
        composeRule.setContent { NudgeAlarmTheme {
            SnoozePickerDialog("PAUSE ALL ALERTS", "Pause alerts", {}, {}, confirmLabel = "PAUSE")
        } }
        val choices = listOf(5, 10, 15, 30, 60, 120, 240, 480, 1440)
        val bounds = choices.map {
            composeRule.onNodeWithTag("quick_pick_$it").performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        // Scroll back before comparing layout coordinates.
        composeRule.onNodeWithTag("quick_pick_5").performScrollTo()
        val layout = choices.map { composeRule.onNodeWithTag("quick_pick_$it").fetchSemanticsNode().boundsInRoot }
        layout.chunked(3).forEach { row ->
            assertEquals(row[0].top, row[1].top)
            assertEquals(row[1].top, row[2].top)
            assertTrue(row[0].left < row[1].left && row[1].left < row[2].left)
        }
        assertTrue(layout[0].top < layout[3].top && layout[3].top < layout[6].top)
        assertEquals(9, bounds.size)
        composeRule.onNodeWithText("PAUSE").assertDoesNotExist()
    }

    @Test fun quickChoiceAppliesImmediatelyAndSavesHistory() {
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
        composeRule.onNodeWithText("HOURS").assertIsDisplayed()
        composeRule.onNodeWithText("MINUTES").assertIsDisplayed()
        composeRule.onNodeWithText("SNOOZE").assertDoesNotExist()
        composeRule.onNodeWithText("2h").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(120, selectedMinutes)
            assertEquals(120, SnoozePreferences(composeRule.activity).read().lastMinutes)
        }
    }

    @Test fun recentChoicePausesImmediatelyWithoutConfirmation() {
        SnoozePreferences(composeRule.activity).record(97)
        SnoozePreferences(composeRule.activity).record(30)
        var selectedMinutes: Int? = null
        composeRule.setContent { NudgeAlarmTheme {
            SnoozePickerDialog("PAUSE ALL ALERTS", "Pause all alerts", { selectedMinutes = it }, {}, confirmLabel = "PAUSE")
        } }
        composeRule.onNodeWithText("PAUSE").assertDoesNotExist()
        composeRule.onNodeWithText("RECENT ▾").performScrollTo().performClick()
        composeRule.onAllNodesWithText("1h 37m")[1].performClick()
        composeRule.runOnIdle {
            assertEquals(97, selectedMinutes)
            assertEquals(97, SnoozePreferences(composeRule.activity).read().lastMinutes)
        }
    }

    @Test fun restoresCustomDurationAndOffersRecentMenu() {
        SnoozePreferences(composeRule.activity).record(97)
        composeRule.setContent {
            NudgeAlarmTheme {
                SnoozePickerDialog("SNOOZE QUEST", "When should this nag again?", {}, {})
            }
        }
        composeRule.onNodeWithText("Remind me in 1h 37m", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("RECENT ▾").performScrollTo().performClick()
        composeRule.onAllNodesWithText("1h 37m").assertCountEquals(2)
    }

    @Test fun bothWheelsCanBeSpunToChooseCustomDuration() {
        var selectedMinutes = 0
        composeRule.setContent {
            NudgeAlarmTheme {
                SnoozePickerDialog("SNOOZE QUEST", "When should this nag again?", { selectedMinutes = it }, {})
            }
        }
        composeRule.onNodeWithTag("snooze_hours_wheel").performTouchInput { swipeUp(durationMillis = 600) }
        composeRule.onNodeWithTag("snooze_minutes_wheel").performTouchInput { swipeUp(durationMillis = 600) }
        composeRule.runOnIdle { assertEquals(0, selectedMinutes) }
        composeRule.onNodeWithText("SNOOZE").performClick()
        composeRule.runOnIdle {
            assertTrue("Hours wheel must increase the duration", selectedMinutes >= 60)
            assertTrue("Minutes wheel must change the initial 15 minutes", selectedMinutes % 60 != 15)
            assertEquals(selectedMinutes, SnoozePreferences(composeRule.activity).read().lastMinutes)
        }
    }
}
