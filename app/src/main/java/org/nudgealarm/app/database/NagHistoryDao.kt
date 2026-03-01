package org.nudgealarm.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

data class DailyAggregate(
    val date: Long,           // Start of day timestamp
    val completed: Int,
    val expired: Int,
    val cancelled: Int
)

data class RuleStats(
    val ruleId: String,
    val title: String,
    val completedCount: Int,
    val expiredCount: Int,
    val cancelledCount: Int,
    val avgResponseTimeMs: Long
)

data class OutcomeCounts(
    val completed: Int,
    val expired: Int,
    val cancelled: Int
)

@Dao
interface NagHistoryDao {

    /**
     * Record a completion event.
     */
    @Insert
    suspend fun insert(history: NagHistoryEntity): Long

    /**
     * Get all history records in a time range.
     */
    @Query("SELECT * FROM nag_history WHERE completedAt >= :startTime AND completedAt < :endTime ORDER BY completedAt DESC")
    suspend fun getInRange(startTime: Long, endTime: Long): List<NagHistoryEntity>

    /**
     * Get daily aggregate counts by outcome for charting.
     * Groups by day (using completedAt / 86400000 * 86400000 to get start of day).
     */
    @Query("""
        SELECT
            (completedAt / 86400000) * 86400000 AS date,
            SUM(CASE WHEN outcome = 'COMPLETED' THEN 1 ELSE 0 END) AS completed,
            SUM(CASE WHEN outcome = 'EXPIRED' THEN 1 ELSE 0 END) AS expired,
            SUM(CASE WHEN outcome = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled
        FROM nag_history
        WHERE completedAt >= :startTime AND completedAt < :endTime
        GROUP BY (completedAt / 86400000)
        ORDER BY date ASC
    """)
    suspend fun getDailyAggregates(startTime: Long, endTime: Long): List<DailyAggregate>

    /**
     * Get statistics per rule for the given time range.
     */
    @Query("""
        SELECT
            ruleId,
            title,
            SUM(CASE WHEN outcome = 'COMPLETED' THEN 1 ELSE 0 END) AS completedCount,
            SUM(CASE WHEN outcome = 'EXPIRED' THEN 1 ELSE 0 END) AS expiredCount,
            SUM(CASE WHEN outcome = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelledCount,
            AVG(CASE WHEN outcome = 'COMPLETED' THEN responseTimeMs ELSE NULL END) AS avgResponseTimeMs
        FROM nag_history
        WHERE completedAt >= :startTime AND completedAt < :endTime
        GROUP BY ruleId
        ORDER BY completedCount DESC
    """)
    suspend fun getStatsByRule(startTime: Long, endTime: Long): List<RuleStats>

    /**
     * Get total counts by outcome for summary display.
     */
    @Query("""
        SELECT
            SUM(CASE WHEN outcome = 'COMPLETED' THEN 1 ELSE 0 END) AS completed,
            SUM(CASE WHEN outcome = 'EXPIRED' THEN 1 ELSE 0 END) AS expired,
            SUM(CASE WHEN outcome = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled
        FROM nag_history
        WHERE completedAt >= :startTime AND completedAt < :endTime
    """)
    suspend fun getOutcomeCounts(startTime: Long, endTime: Long): OutcomeCounts?

    /**
     * Get the earliest record timestamp (for "All Time" range).
     */
    @Query("SELECT MIN(completedAt) FROM nag_history")
    suspend fun getEarliestTimestamp(): Long?

    /**
     * Get total count of history records.
     */
    @Query("SELECT COUNT(*) FROM nag_history")
    suspend fun getTotalCount(): Int

    /**
     * Get all history records (for export).
     */
    @Query("SELECT * FROM nag_history ORDER BY completedAt DESC")
    suspend fun getAll(): List<NagHistoryEntity>

    /**
     * Insert all history records (for import).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(history: List<NagHistoryEntity>)

    /**
     * Delete all history records (for import reset).
     */
    @Query("DELETE FROM nag_history")
    suspend fun deleteAll()
}
