package org.nudgealarm.app.database

import org.junit.Assert.*
import org.junit.Test
import org.nudgealarm.app.core.config.ReminderConfig
import kotlin.time.Duration.Companion.minutes

class ReminderEntityTest {

    @Test
    fun createEntityWithAllFields() {
        val now = System.currentTimeMillis()
        val entity = ReminderEntity(
            id = "test_reminder",
            title = "Test Reminder",
            schedule = "0 8 * * *",
            nagIntervalMinutes = 10,
            maxNags = 50,
            enabled = true,
            createdAt = now,
            updatedAt = now
        )

        assertEquals("test_reminder", entity.id)
        assertEquals("Test Reminder", entity.title)
        assertEquals("0 8 * * *", entity.schedule)
        assertEquals(10, entity.nagIntervalMinutes)
        assertEquals(50, entity.maxNags)
        assertTrue(entity.enabled)
        assertEquals(now, entity.createdAt)
        assertEquals(now, entity.updatedAt)
    }

    @Test
    fun createEntityWithDefaults() {
        val entity = ReminderEntity(
            id = "default_test",
            title = "Default Test",
            schedule = "30 7 * * *"
        )

        assertEquals(5, entity.nagIntervalMinutes)
        assertEquals(100, entity.maxNags)
        assertTrue(entity.enabled)
    }

    @Test
    fun toReminderConfigConvertsCorrectly() {
        val entity = ReminderEntity(
            id = "convert_test",
            title = "Convert Test",
            schedule = "0 9 * * 1-5",
            nagIntervalMinutes = 15,
            maxNags = 20
        )

        val config = entity.toReminderConfig()

        assertEquals("convert_test", config.id)
        assertEquals("Convert Test", config.title)
        assertEquals("0 9 * * 1-5", config.schedule)
        assertEquals(15.minutes, config.nagInterval)
        assertEquals(20, config.maxNags)
    }

    @Test
    fun fromReminderConfigCreatesEntity() {
        val config = ReminderConfig(
            id = "from_config",
            title = "From Config",
            schedule = "0 12 * * *",
            nagInterval = 20.minutes,
            maxNags = 75
        )

        val entity = ReminderEntity.fromReminderConfig(config)

        assertEquals("from_config", entity.id)
        assertEquals("From Config", entity.title)
        assertEquals("0 12 * * *", entity.schedule)
        assertEquals(20, entity.nagIntervalMinutes)
        assertEquals(75, entity.maxNags)
    }

    @Test
    fun roundTripConversion() {
        val originalEntity = ReminderEntity(
            id = "roundtrip",
            title = "Round Trip Test",
            schedule = "0 6 * * *",
            nagIntervalMinutes = 10,
            maxNags = 100
        )

        val config = originalEntity.toReminderConfig()
        val convertedEntity = ReminderEntity.fromReminderConfig(config)

        assertEquals(originalEntity.id, convertedEntity.id)
        assertEquals(originalEntity.title, convertedEntity.title)
        assertEquals(originalEntity.schedule, convertedEntity.schedule)
        assertEquals(originalEntity.nagIntervalMinutes, convertedEntity.nagIntervalMinutes)
        assertEquals(originalEntity.maxNags, convertedEntity.maxNags)
    }

    @Test
    fun entityWithDisabledState() {
        val entity = ReminderEntity(
            id = "disabled",
            title = "Disabled Reminder",
            schedule = "0 8 * * *",
            enabled = false
        )

        assertFalse(entity.enabled)
    }

    @Test
    fun entityEquality() {
        val now = 1000L
        val entity1 = ReminderEntity(
            id = "test",
            title = "Test",
            schedule = "0 8 * * *",
            nagIntervalMinutes = 5,
            maxNags = 100,
            enabled = true,
            createdAt = now,
            updatedAt = now
        )

        val entity2 = ReminderEntity(
            id = "test",
            title = "Test",
            schedule = "0 8 * * *",
            nagIntervalMinutes = 5,
            maxNags = 100,
            enabled = true,
            createdAt = now,
            updatedAt = now
        )

        assertEquals(entity1, entity2)
    }

    @Test
    fun entityCopy() {
        val original = ReminderEntity(
            id = "original",
            title = "Original",
            schedule = "0 8 * * *"
        )

        val updated = original.copy(
            title = "Updated Title",
            enabled = false
        )

        assertEquals("original", updated.id)
        assertEquals("Updated Title", updated.title)
        assertFalse(updated.enabled)
        assertEquals(original.schedule, updated.schedule)
    }

    @Test
    fun weekdayScheduleConversion() {
        val entity = ReminderEntity(
            id = "weekday",
            title = "Weekday Reminder",
            schedule = "0 9 * * 1-5"
        )

        val config = entity.toReminderConfig()
        assertEquals("0 9 * * 1-5", config.schedule)
    }

    @Test
    fun weekendScheduleConversion() {
        val entity = ReminderEntity(
            id = "weekend",
            title = "Weekend Reminder",
            schedule = "0 10 * * 0,6"
        )

        val config = entity.toReminderConfig()
        assertEquals("0 10 * * 0,6", config.schedule)
    }
}
