package org.nudgealarm.app.ui

import org.junit.Assert.*
import org.junit.Test
import org.nudgealarm.app.database.ReminderEntity

class EditRemindersUiStateTest {

    @Test
    fun defaultUiState() {
        val state = EditRemindersUiState()

        assertTrue(state.reminders.isEmpty())
        assertTrue(state.isLoading)
        assertFalse(state.isAddingNew)
        assertNull(state.editingReminder)
    }

    @Test
    fun uiStateWithReminders() {
        val reminders = listOf(
            ReminderEntity("r1", "Reminder 1", "0 8 * * *"),
            ReminderEntity("r2", "Reminder 2", "0 12 * * *")
        )

        val state = EditRemindersUiState(
            reminders = reminders,
            isLoading = false
        )

        assertEquals(2, state.reminders.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun uiStateAddingNew() {
        val state = EditRemindersUiState(
            isAddingNew = true,
            isLoading = false
        )

        assertTrue(state.isAddingNew)
        assertNull(state.editingReminder)
    }

    @Test
    fun uiStateEditingReminder() {
        val reminder = ReminderEntity("edit_test", "Edit Test", "0 9 * * *")
        val state = EditRemindersUiState(
            editingReminder = reminder,
            isLoading = false
        )

        assertFalse(state.isAddingNew)
        assertNotNull(state.editingReminder)
        assertEquals("edit_test", state.editingReminder?.id)
    }

    @Test
    fun uiStateCopy() {
        val original = EditRemindersUiState(isLoading = true)
        val updated = original.copy(isLoading = false, isAddingNew = true)

        assertFalse(updated.isLoading)
        assertTrue(updated.isAddingNew)
    }

    @Test
    fun uiStateEquality() {
        val state1 = EditRemindersUiState(isLoading = false)
        val state2 = EditRemindersUiState(isLoading = false)

        assertEquals(state1, state2)
    }
}
