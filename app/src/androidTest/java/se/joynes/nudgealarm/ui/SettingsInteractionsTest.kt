package se.joynes.nudgealarm.ui

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.storage.AlarmSound
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme

class SettingsInteractionsTest {
    @get:Rule val compose = createComposeRule()
    private fun click(text: String) { compose.onNodeWithText(text).performScrollTo().performClick() }

    @Test fun audioRetentionAndNestedPlaceOptionsDispatchTheChosenValues() {
        val custom = Uri.parse("content://test/custom")
        var state by mutableStateOf(SettingsUiState(customSoundName = "My sound", customSoundUri = custom,
            currentlyPlayingUri = custom, oldReminderRetentionMinutes = 47,
            deviceRingtones = (1..51).map { DeviceRingtone("Device $it", Uri.parse("content://test/$it")) }))
        val events = mutableListOf<String>()
        compose.setContent { NudgeAlarmTheme {
            SettingsScreen(state,
                onSelectAlarmSound = { events.add("alarm:$it"); state = state.copy(alarmSound = it, customSoundName = null, customSoundUri = null) },
                onSelectCustomSound = { name, uri -> events.add("custom:$name:$uri"); state = state.copy(customSoundName = name, customSoundUri = uri) },
                onToggleVibration = { events.add("vibrate:$it"); state = state.copy(vibrationEnabled = it) },
                onPreviewSound = { events.add("preview:$it") }, onStopPreview = {},
                onNavigateToPermissions = { events.add("permissions") },
                onSetOldReminderRetentionMinutes = { events.add("retention:$it"); state = state.copy(oldReminderRetentionMinutes = it) },
                onToggleAlertOnlyWhenActive = { events.add("active:$it"); state = state.copy(alertOnlyWhenActive = it) },
                onSetMinAlertIntervalMinutes = { events.add("interval:$it"); state = state.copy(minAlertIntervalMinutes = it) },
                onRenamePlace = { _, _ -> }, onUpdatePosition = { _, _, _, _, _, _ -> },
                onSelectPosition = { _, _ -> }, onDeletePosition = { _, _ -> }, onDeletePlace = {},
                onBack = { events.add("back") })
        } }
        click("...")
        click("  ${AlarmSound.entries.last().displayName}")
        click(">> MORE SOUNDS")
        compose.onNodeWithText("... and 1 more").assertExists()
        click("  Device 1")
        click(">> MORE SOUNDS") // Collapse so other settings remain easy to reach.
        compose.onAllNodes(isToggleable())[0].performScrollTo().performClick()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.onAllNodes(isToggleable())[1].performScrollTo().performClick()
        click("2h")
        click(">> PERMISSIONS")
        compose.onNodeWithTag("open-saved-places").performScrollTo().performClick()
        compose.onNodeWithText("SAVED PLACES", substring = false).assertIsDisplayed()
        compose.onNodeWithText("BACK TO OPTIONS").performClick()
        compose.onNodeWithText("< BACK TO GAME").performClick()
        compose.runOnIdle {
            assertTrue(events.containsAll(listOf("preview:$custom", "alarm:${AlarmSound.entries.last()}",
                "custom:Device 1:content://test/1", "vibrate:false", "retention:15", "active:true",
                "interval:120", "permissions", "back")))
        }
    }
}
