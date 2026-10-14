package se.joynes.nudgealarm.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.storage.*
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme

class SnoozeSettingsUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun clear() { rule.activity.getSharedPreferences("snooze_picker", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun durationsCanBeAddedEditedDeletedAndAdaptiveSuggestionsDisabled() {
        val prefs = SnoozePreferences(rule.activity)
        prefs.saveConfiguration(SnoozeConfiguration(durationPresets = listOf(5)))
        rule.setContent { NudgeAlarmTheme { SnoozeSettingsScreen({}) } }
        rule.onNodeWithTag("snooze-adaptive").performScrollTo().performClick()
        rule.runOnIdle { assertFalse(prefs.configuration().adaptive) }
        rule.onNodeWithText("+ ADD DURATION").performScrollTo().performClick()
        rule.onNodeWithText("SAVE DURATION").performClick()
        rule.runOnIdle { assertEquals(listOf(5, 15), prefs.configuration().durationPresets) }
        rule.onNodeWithTag("edit-duration-15").performScrollTo().performClick()
        rule.onNodeWithTag("preset_hours_wheel").performTouchInput { swipeUp(durationMillis = 600) }
        rule.onNodeWithText("SAVE DURATION").performClick()
        var edited = 0
        rule.runOnIdle {
            edited = prefs.configuration().durationPresets.last()
            assertTrue(edited >= 60)
            assertEquals(2, prefs.configuration().durationPresets.size)
        }
        rule.onNodeWithTag("delete-duration-$edited").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf(5), prefs.configuration().durationPresets) }
    }

    @Test fun clockShortcutsCanBeManagedAndDefaultTabChangedInSettings() {
        val prefs = SnoozePreferences(rule.activity)
        prefs.saveConfiguration(SnoozeConfiguration(durationPresets = emptyList()))
        rule.setContent { NudgeAlarmTheme { SnoozeSettingsScreen({}) } }
        rule.onNodeWithText("+ ADD CLOCK SHORTCUT").performScrollTo().performClick()
        rule.onNodeWithTag("clock_shortcut_name").performTextReplacement("Lunch")
        rule.onNodeWithText("SAVE SHORTCUT").performClick()
        var id = ""
        rule.runOnIdle { id = prefs.clockShortcuts().single().id }
        rule.onNodeWithTag("settings-edit-clock-$id").performScrollTo().performClick()
        rule.onNodeWithTag("clock_shortcut_name").performTextReplacement("Dinner")
        rule.onNodeWithText("SAVE SHORTCUT").performClick()
        rule.runOnIdle { assertEquals("Dinner", prefs.clockShortcuts().single().name) }
        rule.onNodeWithTag("default-mode-clock").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("clock", prefs.configuration().defaultMode) }
        rule.onNodeWithTag("settings-edit-clock-$id").performScrollTo().performClick()
        rule.onNodeWithText("DELETE SHORTCUT").performScrollTo().performClick()
        rule.runOnIdle { assertTrue(prefs.clockShortcuts().isEmpty()) }
    }

    @Test fun pickerUsesConfiguredFixedChoicesDefaultDurationAndHiddenRecents() {
        val prefs = SnoozePreferences(rule.activity)
        prefs.record(97)
        prefs.saveConfiguration(SnoozeConfiguration(listOf(25, 45), 2, false, 0, 25, false))
        rule.setContent { NudgeAlarmTheme { SnoozePickerDialog("SNOOZE QUEST", "Choose a time", {}, {}) } }
        rule.onNodeWithTag("quick_pick_25").assertExists()
        rule.onNodeWithTag("quick_pick_45").assertExists()
        rule.onNodeWithTag("quick_pick_97").assertDoesNotExist()
        rule.onNodeWithText("RECENT ▾").assertDoesNotExist()
        rule.onNodeWithText("Remind me in 25m", substring = true).assertExists()
    }

    @Test fun importPreviewRequiresExplicitApplyAndSupportsCancel() {
        var applied = false
        var cancelled = false
        val current = SettingsConfiguration()
        rule.setContent { NudgeAlarmTheme {
            SettingsImportPreview(current.copy(vibrationEnabled = false), current,
                onCancel = { cancelled = true }, onApply = { applied = true })
        } }
        rule.onNodeWithText("vibrationEnabled: true → false").assertIsDisplayed()
        rule.runOnIdle { assertFalse(applied) }
        rule.onNodeWithText("CANCEL").performClick()
        rule.runOnIdle { assertTrue(cancelled); assertFalse(applied) }
        rule.onNodeWithTag("confirm-settings-import").performClick()
        rule.runOnIdle { assertTrue(applied) }
    }

    @Test fun optionsContainsSeparateSnoozeAndTransferSections() {
        rule.setContent { NudgeAlarmTheme {
            SettingsScreen(SettingsUiState(), {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {},
                { _, _ -> }, { _, _, _, _, _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {})
        } }
        rule.onNodeWithTag("open-snooze-settings").performClick()
        rule.onNodeWithText("SNOOZE TIMES").assertIsDisplayed()
        rule.onNodeWithText("BACK TO OPTIONS").performClick()
        rule.onNodeWithTag("open-settings-transfer").performClick()
        rule.onNodeWithTag("export-settings").assertIsDisplayed()
        rule.onNodeWithTag("import-settings").assertIsDisplayed()
        rule.onNodeWithText("BACK TO OPTIONS").performClick()
        rule.onNodeWithTag("open-snooze-settings").assertIsDisplayed()
    }
}
