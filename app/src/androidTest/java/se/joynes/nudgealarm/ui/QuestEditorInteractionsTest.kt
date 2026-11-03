package se.joynes.nudgealarm.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.database.ReminderEntity
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme

class QuestEditorInteractionsTest {
    @get:Rule val compose = createComposeRule()
    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        try { node.performScrollTo() } catch (_: AssertionError) { }
        node.performClick()
    }
    @Test fun searchDayFiltersOrderControlsAndDeleteConfirmationWorkTogether() {
        val events = mutableListOf<String>()
        val rules = listOf(ReminderEntity("weekday", "Weekday quest", "0 9 * * 1-5"),
            ReminderEntity("weekend", "Weekend quest", "0 10 * * 0,6"),
            ReminderEntity("monthly", "Monthly quest", "0 11 1 * *"))
        var state by mutableStateOf(EditRemindersUiState(isLoading = false, reminders = rules))
        compose.setContent { NudgeAlarmTheme {
            EditRemindersScreen(state, onAddNew = { events.add("new") },
                onEdit = { state = state.copy(editingReminder = it) }, onDelete = { events.add("delete:$it") },
                onToggleEnabled = { events.add("toggle:$it") }, onMove = { id, direction -> events.add("move:$id:$direction") },
                onSave = { _, _, _, _, _, _ -> }, onCreateSavedPlace = { _, _, _, _ -> },
                onAddSavedPosition = { _, _, _, _, _ -> }, onSelectSavedPosition = { _, _ -> },
                onCancelEdit = { state = state.copy(editingReminder = null) }, onBack = {},
                onLoadFromFile = { events.add("load") }, onSaveToFile = { events.add("save") })
        } }
        compose.onNodeWithContentDescription("Move Weekday quest down").performClick()
        compose.onNodeWithContentDescription("Move Weekend quest up").performClick()
        compose.onAllNodes(isToggleable())[0].performClick()
        click("LOAD"); click("SAVE")
        click("WEEKDAY")
        compose.onNodeWithText("Weekday quest").assertExists()
        compose.onNodeWithText("Weekend quest").assertDoesNotExist()
        click("WEEKEND")
        compose.onNodeWithText("Weekend quest").assertExists()
        compose.onNodeWithText("Weekday quest").assertDoesNotExist()
        click("Mon")
        compose.onNodeWithText("Weekday quest").assertExists()
        click("ALL")
        compose.onNodeWithText("Search quests...").performTextInput("no such quest")
        compose.onNodeWithText("NO MATCHES").assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear").performClick()
        compose.onNodeWithText("Weekday quest").performClick()
        click("DELETE QUEST")
        compose.onNodeWithText("Delete this quest permanently?").assertIsDisplayed()
        assertFalse(events.any { it.startsWith("delete:") })
        compose.onAllNodesWithText("< CANCEL")[1].performClick()
        click("DELETE QUEST"); click("DELETE")
        compose.onNodeWithTag("quest-save").assertDoesNotExist()
        assertEquals(listOf("move:weekday:1", "move:weekend:-1", "toggle:weekday", "load", "save", "delete:weekday"), events)
    }
}
