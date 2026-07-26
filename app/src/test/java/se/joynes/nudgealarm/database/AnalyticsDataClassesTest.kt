package se.joynes.nudgealarm.database

import org.junit.Assert.*
import org.junit.Test

class AnalyticsDataClassesTest {

    // === DailyAggregate Tests ===

    @Test
    fun dailyAggregateCreation() {
        val aggregate = DailyAggregate(
            date = 1000L,
            completed = 10,
            expired = 5,
            cancelled = 2
        )

        assertEquals(1000L, aggregate.date)
        assertEquals(10, aggregate.completed)
        assertEquals(5, aggregate.expired)
        assertEquals(2, aggregate.cancelled)
    }

    @Test
    fun dailyAggregateEquality() {
        val agg1 = DailyAggregate(1000L, 10, 5, 2)
        val agg2 = DailyAggregate(1000L, 10, 5, 2)

        assertEquals(agg1, agg2)
        assertEquals(agg1.hashCode(), agg2.hashCode())
    }

    @Test
    fun dailyAggregateCopy() {
        val original = DailyAggregate(1000L, 10, 5, 2)
        val copied = original.copy(completed = 20)

        assertEquals(20, copied.completed)
        assertEquals(original.date, copied.date)
        assertEquals(original.expired, copied.expired)
        assertEquals(original.cancelled, copied.cancelled)
    }

    @Test
    fun dailyAggregateWithZeros() {
        val empty = DailyAggregate(1000L, 0, 0, 0)

        assertEquals(0, empty.completed)
        assertEquals(0, empty.expired)
        assertEquals(0, empty.cancelled)
    }

    // === RuleStats Tests ===

    @Test
    fun ruleStatsCreation() {
        val stats = RuleStats(
            ruleId = "morning_routine",
            title = "Morning Routine",
            completedCount = 50,
            expiredCount = 10,
            cancelledCount = 5,
            avgResponseTimeMs = 60000L
        )

        assertEquals("morning_routine", stats.ruleId)
        assertEquals("Morning Routine", stats.title)
        assertEquals(50, stats.completedCount)
        assertEquals(10, stats.expiredCount)
        assertEquals(5, stats.cancelledCount)
        assertEquals(60000L, stats.avgResponseTimeMs)
    }

    @Test
    fun ruleStatsEquality() {
        val stats1 = RuleStats("r1", "Rule 1", 10, 5, 2, 1000L)
        val stats2 = RuleStats("r1", "Rule 1", 10, 5, 2, 1000L)

        assertEquals(stats1, stats2)
        assertEquals(stats1.hashCode(), stats2.hashCode())
    }

    @Test
    fun ruleStatsCopy() {
        val original = RuleStats("r1", "Original", 10, 5, 2, 1000L)
        val copied = original.copy(title = "Copied")

        assertEquals("Copied", copied.title)
        assertEquals(original.ruleId, copied.ruleId)
        assertEquals(original.completedCount, copied.completedCount)
    }

    @Test
    fun ruleStatsWithZeroResponseTime() {
        val stats = RuleStats("r1", "Test", 0, 0, 0, 0L)
        assertEquals(0L, stats.avgResponseTimeMs)
    }

    @Test
    fun ruleStatsWithLongResponseTime() {
        val oneHourMs = 3600000L
        val stats = RuleStats("r1", "Slow", 5, 1, 0, oneHourMs)
        assertEquals(oneHourMs, stats.avgResponseTimeMs)
    }

    // === OutcomeCounts Tests ===

    @Test
    fun outcomeCountsCreation() {
        val counts = OutcomeCounts(
            completed = 100,
            expired = 20,
            cancelled = 10
        )

        assertEquals(100, counts.completed)
        assertEquals(20, counts.expired)
        assertEquals(10, counts.cancelled)
    }

    @Test
    fun outcomeCountsEquality() {
        val counts1 = OutcomeCounts(100, 20, 10)
        val counts2 = OutcomeCounts(100, 20, 10)

        assertEquals(counts1, counts2)
        assertEquals(counts1.hashCode(), counts2.hashCode())
    }

    @Test
    fun outcomeCountsCopy() {
        val original = OutcomeCounts(100, 20, 10)
        val copied = original.copy(completed = 150)

        assertEquals(150, copied.completed)
        assertEquals(original.expired, copied.expired)
        assertEquals(original.cancelled, copied.cancelled)
    }

    @Test
    fun outcomeCountsWithZeros() {
        val empty = OutcomeCounts(0, 0, 0)

        assertEquals(0, empty.completed)
        assertEquals(0, empty.expired)
        assertEquals(0, empty.cancelled)
    }

    @Test
    fun outcomeCountsTotalCalculation() {
        val counts = OutcomeCounts(100, 20, 10)
        val total = counts.completed + counts.expired + counts.cancelled

        assertEquals(130, total)
    }

    @Test
    fun outcomeCountsCompletionRateCalculation() {
        val counts = OutcomeCounts(80, 10, 10)
        val total = counts.completed + counts.expired + counts.cancelled
        val rate = if (total > 0) counts.completed.toFloat() / total else 0f

        assertEquals(0.8f, rate, 0.001f)
    }

    @Test
    fun outcomeCountsCompletionRateWithZeroTotal() {
        val counts = OutcomeCounts(0, 0, 0)
        val total = counts.completed + counts.expired + counts.cancelled
        val rate = if (total > 0) counts.completed.toFloat() / total else 0f

        assertEquals(0f, rate, 0.001f)
    }

    // === Cross-class interactions ===

    @Test
    fun aggregateToOutcomeCountsConsistency() {
        val aggregate = DailyAggregate(1000L, 10, 5, 2)
        val counts = OutcomeCounts(
            aggregate.completed,
            aggregate.expired,
            aggregate.cancelled
        )

        assertEquals(aggregate.completed, counts.completed)
        assertEquals(aggregate.expired, counts.expired)
        assertEquals(aggregate.cancelled, counts.cancelled)
    }
}
