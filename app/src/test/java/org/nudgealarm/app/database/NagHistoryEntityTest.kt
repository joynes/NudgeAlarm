package org.nudgealarm.app.database

import org.junit.Assert.*
import org.junit.Test

class NagHistoryEntityTest {

    @Test
    fun createEntityWithAllFields() {
        val entity = NagHistoryEntity(
            id = 1L,
            ruleId = "morning_routine",
            title = "Take vitamins",
            scheduledTime = 1000L,
            triggeredAt = 1100L,
            completedAt = 1200L,
            outcome = "COMPLETED",
            nagCount = 3,
            maxNags = 100,
            responseTimeMs = 100L
        )

        assertEquals(1L, entity.id)
        assertEquals("morning_routine", entity.ruleId)
        assertEquals("Take vitamins", entity.title)
        assertEquals(1000L, entity.scheduledTime)
        assertEquals(1100L, entity.triggeredAt)
        assertEquals(1200L, entity.completedAt)
        assertEquals("COMPLETED", entity.outcome)
        assertEquals(3, entity.nagCount)
        assertEquals(100, entity.maxNags)
        assertEquals(100L, entity.responseTimeMs)
    }

    @Test
    fun createEntityWithDefaultId() {
        val entity = NagHistoryEntity(
            ruleId = "test",
            title = "Test",
            scheduledTime = 1000L,
            triggeredAt = 1100L,
            completedAt = 1200L,
            outcome = "COMPLETED",
            nagCount = 1,
            maxNags = 50,
            responseTimeMs = 100L
        )

        assertEquals(0L, entity.id)
    }

    @Test
    fun entityWithExpiredOutcome() {
        val entity = NagHistoryEntity(
            ruleId = "test",
            title = "Expired Test",
            scheduledTime = 1000L,
            triggeredAt = 1100L,
            completedAt = 2000L,
            outcome = NagStatus.EXPIRED.name,
            nagCount = 100,
            maxNags = 100,
            responseTimeMs = 900L
        )

        assertEquals("EXPIRED", entity.outcome)
        assertEquals(100, entity.nagCount)
    }

    @Test
    fun entityWithCancelledOutcome() {
        val entity = NagHistoryEntity(
            ruleId = "test",
            title = "Cancelled Test",
            scheduledTime = 1000L,
            triggeredAt = 1100L,
            completedAt = 1500L,
            outcome = NagStatus.CANCELLED.name,
            nagCount = 5,
            maxNags = 100,
            responseTimeMs = 400L
        )

        assertEquals("CANCELLED", entity.outcome)
    }

    @Test
    fun responseTimeCalculation() {
        val triggeredAt = System.currentTimeMillis()
        val completedAt = triggeredAt + 60000L // 1 minute later

        val entity = NagHistoryEntity(
            ruleId = "test",
            title = "Response Time Test",
            scheduledTime = triggeredAt - 1000L,
            triggeredAt = triggeredAt,
            completedAt = completedAt,
            outcome = "COMPLETED",
            nagCount = 1,
            maxNags = 100,
            responseTimeMs = completedAt - triggeredAt
        )

        assertEquals(60000L, entity.responseTimeMs)
    }

    @Test
    fun entityEquality() {
        val entity1 = NagHistoryEntity(
            id = 1L,
            ruleId = "test",
            title = "Test",
            scheduledTime = 1000L,
            triggeredAt = 1100L,
            completedAt = 1200L,
            outcome = "COMPLETED",
            nagCount = 1,
            maxNags = 100,
            responseTimeMs = 100L
        )

        val entity2 = NagHistoryEntity(
            id = 1L,
            ruleId = "test",
            title = "Test",
            scheduledTime = 1000L,
            triggeredAt = 1100L,
            completedAt = 1200L,
            outcome = "COMPLETED",
            nagCount = 1,
            maxNags = 100,
            responseTimeMs = 100L
        )

        assertEquals(entity1, entity2)
        assertEquals(entity1.hashCode(), entity2.hashCode())
    }

    @Test
    fun entityCopy() {
        val original = NagHistoryEntity(
            id = 1L,
            ruleId = "test",
            title = "Original",
            scheduledTime = 1000L,
            triggeredAt = 1100L,
            completedAt = 1200L,
            outcome = "COMPLETED",
            nagCount = 1,
            maxNags = 100,
            responseTimeMs = 100L
        )

        val copied = original.copy(title = "Copied")

        assertEquals("Copied", copied.title)
        assertEquals(original.id, copied.id)
        assertEquals(original.ruleId, copied.ruleId)
    }
}
