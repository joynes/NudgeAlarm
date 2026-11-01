package se.joynes.nudgealarm.ui

import org.junit.Assert.*
import org.junit.Test

class OutcomeChartTest {
    @Test fun longRangesKeepEveryOutcomeAndEmptyPeriodInOrder() {
        val days = (0..365).map { ChartDataPoint(it * 86_400_000L, "$it", it % 7, it % 3, it % 5) }
        val buckets = outcomeChartBuckets(days)
        assertTrue(buckets.size <= 30)
        assertEquals(days.sumOf { it.completed }, buckets.sumOf { it.completed })
        assertEquals(days.sumOf { it.expired }, buckets.sumOf { it.expired })
        assertEquals(days.sumOf { it.cancelled }, buckets.sumOf { it.cancelled })
        assertEquals(days.first().date, buckets.first().date)
        assertTrue(buckets.zipWithNext().all { (a, b) -> a.date < b.date })
        assertTrue(buckets.first().label.contains(" – "))
    }

    @Test fun shortRangesKeepDaysWithoutResultsAndSingleOutcomeDays() {
        val days = listOf(ChartDataPoint(0, "", 0, 0, 0), ChartDataPoint(86_400_000, "", 0, 2, 0),
            ChartDataPoint(172_800_000, "", 0, 0, 3))
        val buckets = outcomeChartBuckets(days)
        assertEquals(listOf(0, 2, 3), buckets.map { it.total })
        assertTrue(outcomeChartBuckets(emptyList()).isEmpty())
    }
}
