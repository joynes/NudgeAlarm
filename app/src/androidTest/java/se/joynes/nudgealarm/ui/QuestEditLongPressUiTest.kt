package se.joynes.nudgealarm.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.core.config.ReminderConfig
import se.joynes.nudgealarm.core.event.Event
import se.joynes.nudgealarm.database.ReminderEntity
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme

class QuestEditLongPressUiTest {
    @get:Rule val composeRule = createComposeRule()
    private val water = ReminderConfig("water", "Water quest", "0 8 * * *")

    @Test fun questListLongPressOpensTheCorrectQuest() {
        var edited = ""
        composeRule.setContent { NudgeAlarmTheme {
            RoutinesScreen(listOf(water, water.copy(id="walk",title="Walk quest")), { edited=it }, {})
        } }
        composeRule.onNodeWithText("> WALK QUEST").performTouchInput { longClick() }
        assertEquals("walk", edited)
    }

    @Test fun statusListAndNextTriggerOpenTheirQuest() {
        val edited = mutableListOf<String>()
        composeRule.setContent { NudgeAlarmTheme {
            StatusScreen(false,"water",1L,null,null,listOf(water),{edited.add(it)},{})
        } }
        composeRule.onNodeWithText("Water quest").performTouchInput { longClick() }
        composeRule.onNodeWithText("Next Trigger").performTouchInput { longClick() }
        assertEquals(listOf("water","water"),edited)
    }

    @Test fun onlyCurrentStructuredQuestLogEntriesAreEditable() {
        val edited = mutableListOf<String>()
        composeRule.setContent { NudgeAlarmTheme {
            EventLogScreen(
                listOf(Event.ReminderTriggered(ruleId="water"), Event.ReminderTriggered(ruleId="deleted"), Event.ServiceStarted()),
                setOf("water"), {edited.add(it)}, {}, {}
            )
        } }
        composeRule.onNodeWithText("REMINDER_TRIGGERED: water").performTouchInput { longClick() }
        composeRule.onNodeWithText("REMINDER_TRIGGERED: deleted").performTouchInput { longClick() }
        composeRule.onNodeWithText("SERVICE_STARTED").performTouchInput { longClick() }
        assertEquals(listOf("water"),edited)
    }

    @Test fun activeAndScheduledLongPressGoDirectlyToEditAndTapsKeepOptions() {
        val edited = mutableListOf<String>()
        composeRule.setContent { NudgeAlarmTheme { MainFixture { edited.add(it) } } }
        composeRule.onNodeWithTag("active-quest-active").performScrollTo()
        composeRule.onNodeWithText("! ACTIVE QUEST", useUnmergedTree=true).performTouchInput { longClick() }
        assertEquals(listOf("active"),edited)
        composeRule.onNodeWithText("! ACTIVE QUEST", useUnmergedTree=true).performClick()
        composeRule.onNodeWithText(">> ADVANCED QUEST OPTIONS").assertIsDisplayed()
        composeRule.onNodeWithText("< CLOSE").performClick()
        composeRule.onNodeWithTag("scheduled-quest-later").performScrollTo().performTouchInput { longClick() }
        assertEquals(listOf("active","later"),edited)
    }

    @Test fun editListLongPressDoesNotBreakExportSelectionMode() {
        var edits = 0
        composeRule.setContent { NudgeAlarmTheme {
            EditRemindersScreen(
                uiState=EditRemindersUiState(isLoading=false,reminders=listOf(ReminderEntity("water","Water quest","0 8 * * *"))),
                onAddNew={},onEdit={edits++},onDelete={},onToggleEnabled={},onSave={_,_,_,_,_,_->},
                onCreateSavedPlace={_,_,_,_->},onAddSavedPosition={_,_,_,_,_->},onSelectSavedPosition={_,_->},
                onCancelEdit={},onBack={}
            )
        } }
        composeRule.onNodeWithText("Water quest").performTouchInput { longClick() }
        assertEquals(1,edits)
        composeRule.onNodeWithText("SELECT").performClick()
        composeRule.onNodeWithText("Water quest").performTouchInput { longClick() }
        assertEquals(1,edits)
        composeRule.onNodeWithText("EXPORT 1").assertIsEnabled()
        composeRule.onNodeWithTag("select_water").performClick()
        composeRule.onNodeWithText("EXPORT 0").assertIsNotEnabled()
    }

    @Composable private fun MainFixture(onEdit: (String)->Unit) {
        MainScreen(
            uiState=MainUiState(isServiceRunning=true,hasGameLoaded=true,currentGameName="Test",activeReminders=listOf(ActiveReminderUi("active","Active quest","08:00")),todaysSchedule=listOf(ScheduledReminderUi("later","Later quest","09:00",1L))),
            hasNotificationPermission=true,quietMode=false,onSetQuietMode={},onMenuClick={},showMenu=false,onMenuDismiss={},
            onRequestNotificationPermission={},onSelectFile={},onSaveGame={},onShareGame={},onRenameGame={},
            onExportToClipboard={},onImportFromClipboard={_,_->},onExportData={},onImportData={},onLoadPreset={},
            onLoadSavedGame={},onDeleteSavedGame={},onCreateNewSchema={_,_->},onStartService={},onStopService={},
            onNavigateToPermissions={},onNavigateToEventLog={},onNavigateToRoutines={},onNavigateToEditReminders={},
            onEditReminder=onEdit,onMarkDone={},onMarkAllDone={},onSnooze={_,_->},onCancel={},onCancelAll={},onDeletePermanently={}
        )
    }
}
