package se.joynes.nudgealarm.database

import org.junit.Assert.*
import org.junit.Test

class NagStateEntityTest {

    @Test
    fun createOccurrenceKeyFormatsCorrectly() {
        val key = NagStateEntity.createOccurrenceKey("my_rule", 1706684400000L)
        assertEquals("my_rule@1706684400000", key)
    }

    @Test
    fun parseOccurrenceKeyParsesValidKey() {
        val result = NagStateEntity.parseOccurrenceKey("my_rule@1706684400000")
        assertNotNull(result)
        assertEquals("my_rule", result?.first)
        assertEquals(1706684400000L, result?.second)
    }

    @Test
    fun parseOccurrenceKeyHandlesRuleWithUnderscore() {
        val result = NagStateEntity.parseOccurrenceKey("my_rule_with_underscores@1234567890")
        assertNotNull(result)
        assertEquals("my_rule_with_underscores", result?.first)
        assertEquals(1234567890L, result?.second)
    }

    @Test
    fun parseOccurrenceKeyReturnsNullForInvalidFormat() {
        assertNull(NagStateEntity.parseOccurrenceKey("invalid_key"))
        assertNull(NagStateEntity.parseOccurrenceKey("no_at_symbol"))
        assertNull(NagStateEntity.parseOccurrenceKey("rule@notanumber"))
        assertNull(NagStateEntity.parseOccurrenceKey(""))
    }

    @Test
    fun parseOccurrenceKeyHandlesMultipleAtSymbols() {
        // This should only work if there's exactly one @
        val result = NagStateEntity.parseOccurrenceKey("rule@extra@123")
        assertNull(result) // Should fail because split results in 3 parts
    }

    @Test
    fun entityCreationWithAllFields() {
        val entity = NagStateEntity(
            occurrenceKey = "test@123",
            ruleId = "test",
            scheduledTime = 123L,
            title = "Test Title",
            triggeredAt = 124L,
            nagCount = 1,
            maxNags = 100,
            nagIntervalMs = 300000L,
            status = NagStatus.ACTIVE.name,
            snoozedUntil = null,
            lastNagAt = 124L
        )

        assertEquals("test@123", entity.occurrenceKey)
        assertEquals("test", entity.ruleId)
        assertEquals(123L, entity.scheduledTime)
        assertEquals("Test Title", entity.title)
        assertEquals(124L, entity.triggeredAt)
        assertEquals(1, entity.nagCount)
        assertEquals(100, entity.maxNags)
        assertEquals(300000L, entity.nagIntervalMs)
        assertEquals("ACTIVE", entity.status)
        assertNull(entity.snoozedUntil)
        assertEquals(124L, entity.lastNagAt)
    }

    @Test
    fun entityWithSnoozeValues() {
        val entity = NagStateEntity(
            occurrenceKey = "test@123",
            ruleId = "test",
            scheduledTime = 123L,
            title = "Test",
            triggeredAt = 124L,
            nagCount = 3,
            maxNags = 100,
            nagIntervalMs = 300000L,
            status = NagStatus.SNOOZED.name,
            snoozedUntil = 500L,
            lastNagAt = 400L
        )

        assertEquals(NagStatus.SNOOZED.name, entity.status)
        assertEquals(500L, entity.snoozedUntil)
    }

    @Test
    fun entityEquality() {
        val entity1 = NagStateEntity(
            occurrenceKey = "test@123",
            ruleId = "test",
            scheduledTime = 123L,
            title = "Test",
            triggeredAt = 124L,
            nagCount = 1,
            maxNags = 100,
            nagIntervalMs = 300000L,
            status = NagStatus.ACTIVE.name,
            snoozedUntil = null,
            lastNagAt = 124L
        )

        val entity2 = NagStateEntity(
            occurrenceKey = "test@123",
            ruleId = "test",
            scheduledTime = 123L,
            title = "Test",
            triggeredAt = 124L,
            nagCount = 1,
            maxNags = 100,
            nagIntervalMs = 300000L,
            status = NagStatus.ACTIVE.name,
            snoozedUntil = null,
            lastNagAt = 124L
        )

        assertEquals(entity1, entity2)
        assertEquals(entity1.hashCode(), entity2.hashCode())
    }

    @Test
    fun entityCopy() {
        val original = NagStateEntity(
            occurrenceKey = "test@123",
            ruleId = "test",
            scheduledTime = 123L,
            title = "Original",
            triggeredAt = 124L,
            nagCount = 1,
            maxNags = 100,
            nagIntervalMs = 300000L,
            status = NagStatus.ACTIVE.name,
            snoozedUntil = null,
            lastNagAt = 124L
        )

        val copied = original.copy(
            nagCount = 5,
            status = NagStatus.SNOOZED.name,
            snoozedUntil = 1000L
        )

        assertEquals(5, copied.nagCount)
        assertEquals(NagStatus.SNOOZED.name, copied.status)
        assertEquals(1000L, copied.snoozedUntil)
        assertEquals(original.occurrenceKey, copied.occurrenceKey)
        assertEquals(original.ruleId, copied.ruleId)
    }

    @Test
    fun entityWithCompletedStatus() {
        val entity = NagStateEntity(
            occurrenceKey = "test@123",
            ruleId = "test",
            scheduledTime = 123L,
            title = "Test",
            triggeredAt = 124L,
            nagCount = 5,
            maxNags = 100,
            nagIntervalMs = 300000L,
            status = NagStatus.COMPLETED.name,
            snoozedUntil = null,
            lastNagAt = 500L
        )

        assertEquals(NagStatus.COMPLETED.name, entity.status)
    }

    @Test
    fun entityWithExpiredStatus() {
        val entity = NagStateEntity(
            occurrenceKey = "test@123",
            ruleId = "test",
            scheduledTime = 123L,
            title = "Test",
            triggeredAt = 124L,
            nagCount = 100,
            maxNags = 100,
            nagIntervalMs = 300000L,
            status = NagStatus.EXPIRED.name,
            snoozedUntil = null,
            lastNagAt = 1000L
        )

        assertEquals(NagStatus.EXPIRED.name, entity.status)
        assertEquals(entity.nagCount, entity.maxNags)
    }

    @Test
    fun entityWithCancelledStatus() {
        val entity = NagStateEntity(
            occurrenceKey = "test@123",
            ruleId = "test",
            scheduledTime = 123L,
            title = "Test",
            triggeredAt = 124L,
            nagCount = 3,
            maxNags = 100,
            nagIntervalMs = 300000L,
            status = NagStatus.CANCELLED.name,
            snoozedUntil = null,
            lastNagAt = 300L
        )

        assertEquals(NagStatus.CANCELLED.name, entity.status)
    }

    @Test
    fun createOccurrenceKeyRoundTrip() {
        val ruleId = "morning_vitamins"
        val scheduledTime = 1706684400000L

        val key = NagStateEntity.createOccurrenceKey(ruleId, scheduledTime)
        val parsed = NagStateEntity.parseOccurrenceKey(key)

        assertNotNull(parsed)
        assertEquals(ruleId, parsed?.first)
        assertEquals(scheduledTime, parsed?.second)
    }

    @Test
    fun createOccurrenceKeyWithSpecialChars() {
        val key = NagStateEntity.createOccurrenceKey("rule_with-dash", 123L)
        assertEquals("rule_with-dash@123", key)
    }
}
