package org.nudgealarm.app.ui

import org.junit.Assert.*
import org.junit.Test
import org.nudgealarm.app.database.RuleStats

class AnalyticsUiStateTest {

    // === TimeRange Tests ===

    @Test
    fun timeRangeEntriesExist() {
        val ranges = TimeRange.entries
        assertEquals(4, ranges.size)
    }

    @Test
    fun timeRangeValues() {
        assertEquals("WEEK", TimeRange.WEEK.name)
        assertEquals("MONTH", TimeRange.MONTH.name)
        assertEquals("YEAR", TimeRange.YEAR.name)
        assertEquals("ALL", TimeRange.ALL.name)
    }

    @Test
    fun timeRangeOrdinalOrder() {
        assertEquals(0, TimeRange.WEEK.ordinal)
        assertEquals(1, TimeRange.MONTH.ordinal)
        assertEquals(2, TimeRange.YEAR.ordinal)
        assertEquals(3, TimeRange.ALL.ordinal)
    }

    // === ChartDataPoint Tests ===

    @Test
    fun chartDataPointCreation() {
        val point = ChartDataPoint(
            date = 1000L,
            label = "Mon",
            completed = 5,
            expired = 2,
            cancelled = 1
        )

        assertEquals(1000L, point.date)
        assertEquals("Mon", point.label)
        assertEquals(5, point.completed)
        assertEquals(2, point.expired)
        assertEquals(1, point.cancelled)
    }

    @Test
    fun chartDataPointTotalCalculation() {
        val point = ChartDataPoint(
            date = 1000L,
            label = "Test",
            completed = 10,
            expired = 5,
            cancelled = 3
        )

        assertEquals(18, point.total)
    }

    @Test
    fun chartDataPointTotalWithZeros() {
        val point = ChartDataPoint(
            date = 1000L,
            label = "Empty",
            completed = 0,
            expired = 0,
            cancelled = 0
        )

        assertEquals(0, point.total)
    }

    @Test
    fun chartDataPointEquality() {
        val point1 = ChartDataPoint(1000L, "Test", 5, 2, 1)
        val point2 = ChartDataPoint(1000L, "Test", 5, 2, 1)

        assertEquals(point1, point2)
    }

    @Test
    fun chartDataPointCopy() {
        val original = ChartDataPoint(1000L, "Original", 5, 2, 1)
        val copied = original.copy(label = "Copied")

        assertEquals("Copied", copied.label)
        assertEquals(original.completed, copied.completed)
    }

    // === AnalyticsUiState Tests ===

    @Test
    fun defaultUiState() {
        val state = AnalyticsUiState()

        assertEquals(TimeRange.WEEK, state.timeRange)
        assertTrue(state.isLoading)
        assertFalse(state.hasData)
        assertEquals(0, state.completedCount)
        assertEquals(0, state.expiredCount)
        assertEquals(0, state.cancelledCount)
        assertEquals(0f, state.completionRate, 0.001f)
        assertTrue(state.chartData.isEmpty())
        assertTrue(state.ruleStats.isEmpty())
    }

    @Test
    fun uiStateWithData() {
        val chartData = listOf(
            ChartDataPoint(1000L, "Mon", 5, 2, 1),
            ChartDataPoint(2000L, "Tue", 3, 1, 0)
        )

        val ruleStats = listOf(
            RuleStats("rule1", "Test Rule", 10, 2, 1, 60000L)
        )

        val state = AnalyticsUiState(
            timeRange = TimeRange.MONTH,
            isLoading = false,
            hasData = true,
            completedCount = 100,
            expiredCount = 20,
            cancelledCount = 5,
            completionRate = 0.8f,
            chartData = chartData,
            ruleStats = ruleStats
        )

        assertEquals(TimeRange.MONTH, state.timeRange)
        assertFalse(state.isLoading)
        assertTrue(state.hasData)
        assertEquals(100, state.completedCount)
        assertEquals(20, state.expiredCount)
        assertEquals(5, state.cancelledCount)
        assertEquals(0.8f, state.completionRate, 0.001f)
        assertEquals(2, state.chartData.size)
        assertEquals(1, state.ruleStats.size)
    }

    @Test
    fun uiStateCopyTimeRange() {
        val original = AnalyticsUiState(timeRange = TimeRange.WEEK)
        val updated = original.copy(timeRange = TimeRange.YEAR)

        assertEquals(TimeRange.YEAR, updated.timeRange)
        assertEquals(original.isLoading, updated.isLoading)
    }

    @Test
    fun uiStateCopyLoadingState() {
        val original = AnalyticsUiState(isLoading = true)
        val updated = original.copy(isLoading = false)

        assertFalse(updated.isLoading)
    }

    @Test
    fun completionRateBoundaries() {
        val zeroState = AnalyticsUiState(completionRate = 0f)
        assertEquals(0f, zeroState.completionRate, 0.001f)

        val fullState = AnalyticsUiState(completionRate = 1f)
        assertEquals(1f, fullState.completionRate, 0.001f)

        val midState = AnalyticsUiState(completionRate = 0.5f)
        assertEquals(0.5f, midState.completionRate, 0.001f)
    }
}
