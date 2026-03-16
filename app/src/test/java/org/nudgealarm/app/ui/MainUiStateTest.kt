package org.nudgealarm.app.ui

import org.junit.Assert.*
import org.junit.Test
import org.nudgealarm.app.core.event.Event

class MainUiStateTest {

    // === ActiveReminderUi Tests ===

    @Test
    fun activeReminderUiCreation() {
        val reminder = ActiveReminderUi(
            ruleId = "morning_vitamins",
            title = "Take morning vitamins",
            triggeredAt = "08:00"
        )

        assertEquals("morning_vitamins", reminder.ruleId)
        assertEquals("Take morning vitamins", reminder.title)
        assertEquals("08:00", reminder.triggeredAt)
        assertNull(reminder.snoozedUntil)
    }

    @Test
    fun activeReminderUiWithSnooze() {
        val reminder = ActiveReminderUi(
            ruleId = "workout",
            title = "Do workout",
            triggeredAt = "07:00",
            snoozedUntil = "07:30"
        )

        assertEquals("07:30", reminder.snoozedUntil)
    }

    @Test
    fun activeReminderUiEquality() {
        val r1 = ActiveReminderUi("r1", "Test", "08:00", null)
        val r2 = ActiveReminderUi("r1", "Test", "08:00", null)

        assertEquals(r1, r2)
    }

    @Test
    fun activeReminderUiCopy() {
        val original = ActiveReminderUi("r1", "Original", "08:00", null)
        val snoozed = original.copy(snoozedUntil = "08:30")

        assertEquals("08:30", snoozed.snoozedUntil)
        assertEquals(original.ruleId, snoozed.ruleId)
    }

    // === ScheduledReminderUi Tests ===

    @Test
    fun scheduledReminderUiCreation() {
        val scheduled = ScheduledReminderUi(
            ruleId = "lunch_break",
            title = "Lunch break",
            scheduledTime = "12:00",
            scheduledTimeMillis = 1000000L
        )

        assertEquals("lunch_break", scheduled.ruleId)
        assertEquals("Lunch break", scheduled.title)
        assertEquals("12:00", scheduled.scheduledTime)
        assertEquals(1000000L, scheduled.scheduledTimeMillis)
    }

    @Test
    fun scheduledReminderUiEquality() {
        val s1 = ScheduledReminderUi("s1", "Test", "12:00", 1000L)
        val s2 = ScheduledReminderUi("s1", "Test", "12:00", 1000L)

        assertEquals(s1, s2)
    }

    @Test
    fun scheduledReminderUiCopy() {
        val original = ScheduledReminderUi("s1", "Original", "12:00", 1000L)
        val updated = original.copy(scheduledTime = "13:00", scheduledTimeMillis = 2000L)

        assertEquals("13:00", updated.scheduledTime)
        assertEquals(2000L, updated.scheduledTimeMillis)
    }

    // === MainUiState Tests ===

    @Test
    fun defaultMainUiState() {
        val state = MainUiState()

        assertFalse(state.isServiceRunning)
        assertNull(state.configFilePath)
        assertNull(state.nextTriggerRule)
        assertNull(state.nextTriggerTime)
        assertEquals(0, state.activeRulesCount)
        assertEquals(0, state.totalRulesCount)
        assertTrue(state.events.isEmpty())
        assertTrue(state.activeReminders.isEmpty())
        assertTrue(state.todaysSchedule.isEmpty())
    }

    @Test
    fun mainUiStateWithServiceRunning() {
        val state = MainUiState(
            isServiceRunning = true,
            configFilePath = "/path/to/config.yaml"
        )

        assertTrue(state.isServiceRunning)
        assertEquals("/path/to/config.yaml", state.configFilePath)
    }

    @Test
    fun mainUiStateWithActiveReminders() {
        val reminders = listOf(
            ActiveReminderUi("r1", "Reminder 1", "08:00"),
            ActiveReminderUi("r2", "Reminder 2", "09:00")
        )

        val state = MainUiState(
            isServiceRunning = true,
            activeReminders = reminders
        )

        assertEquals(2, state.activeReminders.size)
        assertEquals("r1", state.activeReminders[0].ruleId)
    }

    @Test
    fun mainUiStateWithTodaysSchedule() {
        val schedule = listOf(
            ScheduledReminderUi("s1", "Scheduled 1", "10:00", 1000L),
            ScheduledReminderUi("s2", "Scheduled 2", "14:00", 2000L),
            ScheduledReminderUi("s3", "Scheduled 3", "18:00", 3000L)
        )

        val state = MainUiState(
            isServiceRunning = true,
            todaysSchedule = schedule
        )

        assertEquals(3, state.todaysSchedule.size)
    }

    @Test
    fun mainUiStateWithEvents() {
        val events = listOf(
            Event.ServiceStarted(),
            Event.ConfigLoaded(ruleCount = 5)
        )

        val state = MainUiState(events = events)

        assertEquals(2, state.events.size)
    }

    @Test
    fun mainUiStateWithNextTrigger() {
        val state = MainUiState(
            isServiceRunning = true,
            nextTriggerRule = "morning_routine",
            nextTriggerTime = "08:00"
        )

        assertEquals("morning_routine", state.nextTriggerRule)
        assertEquals("08:00", state.nextTriggerTime)
    }

    @Test
    fun mainUiStateWithRuleCounts() {
        val state = MainUiState(
            isServiceRunning = true,
            activeRulesCount = 3,
            totalRulesCount = 10
        )

        assertEquals(3, state.activeRulesCount)
        assertEquals(10, state.totalRulesCount)
    }

    @Test
    fun mainUiStateCopy() {
        val original = MainUiState(
            isServiceRunning = false,
            activeRulesCount = 0
        )

        val updated = original.copy(
            isServiceRunning = true,
            activeRulesCount = 5
        )

        assertTrue(updated.isServiceRunning)
        assertEquals(5, updated.activeRulesCount)
    }

    @Test
    fun mainUiStateEquality() {
        val state1 = MainUiState(isServiceRunning = true, activeRulesCount = 5)
        val state2 = MainUiState(isServiceRunning = true, activeRulesCount = 5)

        assertEquals(state1, state2)
    }

    @Test
    fun mainUiStateFullConfiguration() {
        val activeReminders = listOf(
            ActiveReminderUi("active1", "Active 1", "08:00", "08:30")
        )

        val schedule = listOf(
            ScheduledReminderUi("sched1", "Scheduled 1", "12:00", 1000L)
        )

        val events = listOf(
            Event.ServiceStarted()
        )

        val state = MainUiState(
            isServiceRunning = true,
            configFilePath = "/config.yaml",
            nextTriggerRule = "next_rule",
            nextTriggerTime = "10:00",
            activeRulesCount = 8,
            totalRulesCount = 10,
            events = events,
            activeReminders = activeReminders,
            todaysSchedule = schedule
        )

        assertTrue(state.isServiceRunning)
        assertEquals("/config.yaml", state.configFilePath)
        assertEquals("next_rule", state.nextTriggerRule)
        assertEquals("10:00", state.nextTriggerTime)
        assertEquals(8, state.activeRulesCount)
        assertEquals(10, state.totalRulesCount)
        assertEquals(1, state.events.size)
        assertEquals(1, state.activeReminders.size)
        assertEquals(1, state.todaysSchedule.size)
    }
}
