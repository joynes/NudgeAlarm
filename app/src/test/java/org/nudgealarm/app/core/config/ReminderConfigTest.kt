package org.nudgealarm.app.core.config

import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ReminderConfigTest {

    @Test
    fun createConfigWithRequiredFields() {
        val config = ReminderConfig(
            id = "test_reminder",
            title = "Test Reminder",
            schedule = "0 8 * * *"
        )

        assertEquals("test_reminder", config.id)
        assertEquals("Test Reminder", config.title)
        assertEquals("0 8 * * *", config.schedule)
    }

    @Test
    fun defaultNagInterval() {
        val config = ReminderConfig(
            id = "test",
            title = "Test",
            schedule = "0 8 * * *"
        )

        assertEquals(5.minutes, config.nagInterval)
    }

    @Test
    fun defaultMaxNags() {
        val config = ReminderConfig(
            id = "test",
            title = "Test",
            schedule = "0 8 * * *"
        )

        assertEquals(100, config.maxNags)
    }

    @Test
    fun defaultSound() {
        val config = ReminderConfig(
            id = "test",
            title = "Test",
            schedule = "0 8 * * *"
        )

        assertEquals("alarm", config.sound)
    }

    @Test
    fun defaultVibration() {
        val config = ReminderConfig(
            id = "test",
            title = "Test",
            schedule = "0 8 * * *"
        )

        assertEquals("strong", config.vibration)
    }

    @Test
    fun defaultSnoozeOptions() {
        val config = ReminderConfig(
            id = "test",
            title = "Test",
            schedule = "0 8 * * *"
        )

        assertEquals(2, config.snoozeOptions.size)
        assertEquals(5.minutes, config.snoozeOptions[0])
        assertEquals(15.minutes, config.snoozeOptions[1])
    }

    @Test
    fun configWithCustomValues() {
        val config = ReminderConfig(
            id = "custom",
            title = "Custom Reminder",
            schedule = "30 9 * * 1-5",
            nagInterval = 10.minutes,
            maxNags = 50,
            sound = "ringtone",
            vibration = "gentle",
            snoozeOptions = listOf(10.minutes, 30.minutes, 1.hours)
        )

        assertEquals("custom", config.id)
        assertEquals("Custom Reminder", config.title)
        assertEquals("30 9 * * 1-5", config.schedule)
        assertEquals(10.minutes, config.nagInterval)
        assertEquals(50, config.maxNags)
        assertEquals("ringtone", config.sound)
        assertEquals("gentle", config.vibration)
        assertEquals(3, config.snoozeOptions.size)
        assertEquals(1.hours, config.snoozeOptions[2])
    }

    @Test
    fun configEquality() {
        val config1 = ReminderConfig("test", "Test", "0 8 * * *")
        val config2 = ReminderConfig("test", "Test", "0 8 * * *")

        assertEquals(config1, config2)
        assertEquals(config1.hashCode(), config2.hashCode())
    }

    @Test
    fun configInequality() {
        val config1 = ReminderConfig("test1", "Test 1", "0 8 * * *")
        val config2 = ReminderConfig("test2", "Test 2", "0 8 * * *")

        assertNotEquals(config1, config2)
    }

    @Test
    fun configCopy() {
        val original = ReminderConfig(
            id = "original",
            title = "Original",
            schedule = "0 8 * * *"
        )

        val copied = original.copy(
            id = "copied",
            title = "Copied Title"
        )

        assertEquals("copied", copied.id)
        assertEquals("Copied Title", copied.title)
        assertEquals(original.schedule, copied.schedule)
        assertEquals(original.nagInterval, copied.nagInterval)
    }

    @Test
    fun configWithWeekdaySchedule() {
        val config = ReminderConfig(
            id = "weekday",
            title = "Weekday Reminder",
            schedule = "0 9 * * 1-5"
        )

        assertEquals("0 9 * * 1-5", config.schedule)
    }

    @Test
    fun configWithWeekendSchedule() {
        val config = ReminderConfig(
            id = "weekend",
            title = "Weekend Reminder",
            schedule = "0 10 * * 0,6"
        )

        assertEquals("0 10 * * 0,6", config.schedule)
    }

    @Test
    fun configWithEmptySnoozeOptions() {
        val config = ReminderConfig(
            id = "no_snooze",
            title = "No Snooze",
            schedule = "0 8 * * *",
            snoozeOptions = emptyList()
        )

        assertTrue(config.snoozeOptions.isEmpty())
    }

    @Test
    fun nagIntervalInMinutes() {
        val config = ReminderConfig(
            id = "test",
            title = "Test",
            schedule = "0 8 * * *",
            nagInterval = 15.minutes
        )

        assertEquals(15L, config.nagInterval.inWholeMinutes)
    }

    @Test
    fun configWithLongNagInterval() {
        val config = ReminderConfig(
            id = "test",
            title = "Test",
            schedule = "0 8 * * *",
            nagInterval = 1.hours
        )

        assertEquals(60L, config.nagInterval.inWholeMinutes)
    }
}
