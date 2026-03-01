package org.nudgealarm.app.database

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class AnalyticsRepositoryTest {

    // We test the time range calculation methods that don't require the DAO

    @Test
    fun lastWeekRangeCoversSevenDays() {
        val repo = createRepositoryWithMockDao()
        val (start, end) = repo.getLastWeekRange()

        val diffDays = (end - start) / (24 * 60 * 60 * 1000)
        assertTrue("Range should be approximately 7 days", diffDays >= 6 && diffDays <= 8)
    }

    @Test
    fun lastWeekRangeStartsAtMidnight() {
        val repo = createRepositoryWithMockDao()
        val (start, _) = repo.getLastWeekRange()

        val cal = Calendar.getInstance()
        cal.timeInMillis = start
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun lastWeekRangeEndsAtNow() {
        val before = System.currentTimeMillis()
        val repo = createRepositoryWithMockDao()
        val (_, end) = repo.getLastWeekRange()
        val after = System.currentTimeMillis()

        assertTrue(end >= before)
        assertTrue(end <= after)
    }

    @Test
    fun lastMonthRangeCoversThirtyDays() {
        val repo = createRepositoryWithMockDao()
        val (start, end) = repo.getLastMonthRange()

        val diffDays = (end - start) / (24 * 60 * 60 * 1000)
        assertTrue("Range should be approximately 30 days", diffDays >= 29 && diffDays <= 31)
    }

    @Test
    fun lastMonthRangeStartsAtMidnight() {
        val repo = createRepositoryWithMockDao()
        val (start, _) = repo.getLastMonthRange()

        val cal = Calendar.getInstance()
        cal.timeInMillis = start
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun lastYearRangeCovers365Days() {
        val repo = createRepositoryWithMockDao()
        val (start, end) = repo.getLastYearRange()

        val diffDays = (end - start) / (24 * 60 * 60 * 1000)
        assertTrue("Range should be approximately 365 days", diffDays >= 364 && diffDays <= 366)
    }

    @Test
    fun lastYearRangeStartsAtMidnight() {
        val repo = createRepositoryWithMockDao()
        val (start, _) = repo.getLastYearRange()

        val cal = Calendar.getInstance()
        cal.timeInMillis = start
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun timeRangesAreChronological() {
        val repo = createRepositoryWithMockDao()

        val (weekStart, weekEnd) = repo.getLastWeekRange()
        val (monthStart, monthEnd) = repo.getLastMonthRange()
        val (yearStart, yearEnd) = repo.getLastYearRange()

        // Start times should be in reverse order (year is oldest)
        assertTrue(yearStart < monthStart)
        assertTrue(monthStart < weekStart)

        // End times should be similar (all near now)
        val tolerance = 1000L // 1 second tolerance
        assertTrue(Math.abs(weekEnd - monthEnd) < tolerance)
        assertTrue(Math.abs(monthEnd - yearEnd) < tolerance)
    }

    // Helper to create repository with a mock DAO
    // Since we can't easily mock in pure unit tests without Mockito,
    // we create a minimal implementation for testing time calculations
    private fun createRepositoryWithMockDao(): AnalyticsRepository {
        return AnalyticsRepository(object : NagHistoryDao {
            override suspend fun insert(history: NagHistoryEntity): Long = 1L
            override suspend fun getInRange(startTime: Long, endTime: Long): List<NagHistoryEntity> = emptyList()
            override suspend fun getDailyAggregates(startTime: Long, endTime: Long): List<DailyAggregate> = emptyList()
            override suspend fun getStatsByRule(startTime: Long, endTime: Long): List<RuleStats> = emptyList()
            override suspend fun getOutcomeCounts(startTime: Long, endTime: Long): OutcomeCounts? = null
            override suspend fun getEarliestTimestamp(): Long? = null
            override suspend fun getTotalCount(): Int = 0
            override suspend fun getAll(): List<NagHistoryEntity> = emptyList()
            override suspend fun insertAll(history: List<NagHistoryEntity>) {}
            override suspend fun deleteAll() {}
        })
    }
}
