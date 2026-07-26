package se.joynes.nudgealarm.database

import java.util.Calendar

/**
 * Repository for analytics data management.
 * Handles recording completions to history and retrieving aggregated data.
 */
class AnalyticsRepository(private val dao: NagHistoryDao) {

    /**
     * Record a completion event to history.
     */
    suspend fun recordCompletion(nagState: NagStateEntity, outcome: NagStatus) {
        val completedAt = System.currentTimeMillis()
        val history = NagHistoryEntity(
            ruleId = nagState.ruleId,
            title = nagState.title,
            scheduledTime = nagState.scheduledTime,
            triggeredAt = nagState.triggeredAt,
            completedAt = completedAt,
            outcome = outcome.name,
            nagCount = nagState.nagCount,
            maxNags = nagState.maxNags,
            responseTimeMs = completedAt - nagState.triggeredAt
        )
        dao.insert(history)
    }

    /**
     * Get daily aggregated statistics for charts.
     */
    suspend fun getDailyStats(startTime: Long, endTime: Long): List<DailyAggregate> {
        return dao.getDailyAggregates(startTime, endTime)
    }

    /**
     * Get per-rule statistics breakdown.
     */
    suspend fun getRuleStats(startTime: Long, endTime: Long): List<RuleStats> {
        return dao.getStatsByRule(startTime, endTime)
    }

    /**
     * Get outcome counts for the given range.
     */
    suspend fun getOutcomeCounts(startTime: Long, endTime: Long): OutcomeCounts {
        return dao.getOutcomeCounts(startTime, endTime) ?: OutcomeCounts(0, 0, 0)
    }

    /**
     * Get all history records in a time range.
     */
    suspend fun getHistory(startTime: Long, endTime: Long): List<NagHistoryEntity> {
        return dao.getInRange(startTime, endTime)
    }

    /**
     * Get the time range for last week (7 days).
     */
    fun getLastWeekRange(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.add(Calendar.DAY_OF_YEAR, -7)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis to now
    }

    /**
     * Get the time range for last month (30 days).
     */
    fun getLastMonthRange(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.add(Calendar.DAY_OF_YEAR, -30)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis to now
    }

    /**
     * Get the time range for last year (365 days).
     */
    fun getLastYearRange(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.add(Calendar.DAY_OF_YEAR, -365)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis to now
    }

    /**
     * Get the time range for all data (from earliest record to now).
     */
    suspend fun getAllTimeRange(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val earliest = dao.getEarliestTimestamp()
        return (earliest ?: now) to now
    }

    /**
     * Check if there is any history data.
     */
    suspend fun hasData(): Boolean {
        return dao.getTotalCount() > 0
    }
}
