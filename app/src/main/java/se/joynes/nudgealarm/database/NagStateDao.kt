package se.joynes.nudgealarm.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NagStateDao {

    /**
     * Get all active or snoozed nags (ones that need attention).
     */
    @Query("SELECT * FROM nag_states WHERE status IN ('ACTIVE', 'SNOOZED') ORDER BY triggeredAt DESC")
    fun getActiveNagsFlow(): Flow<List<NagStateEntity>>

    /**
     * Get all active or snoozed nags (non-flow version for one-time reads).
     */
    @Query("SELECT * FROM nag_states WHERE status IN ('ACTIVE', 'SNOOZED') ORDER BY triggeredAt DESC")
    suspend fun getActiveNags(): List<NagStateEntity>

    /**
     * Get a specific nag by its occurrence key.
     */
    @Query("SELECT * FROM nag_states WHERE occurrenceKey = :key")
    suspend fun getByKey(key: String): NagStateEntity?

    /**
     * Check if an occurrence already exists.
     */
    @Query("SELECT COUNT(*) FROM nag_states WHERE occurrenceKey = :key")
    suspend fun existsByKey(key: String): Int

    /**
     * Get all nag states (all statuses, for export).
     */
    @Query("SELECT * FROM nag_states ORDER BY triggeredAt DESC")
    suspend fun getAll(): List<NagStateEntity>

    /**
     * Insert all nag states (for import).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(states: List<NagStateEntity>)

    /**
     * Delete all nag states (for import reset).
     */
    @Query("DELETE FROM nag_states")
    suspend fun deleteAll()

    /**
     * Insert a new nag state. Returns -1 if already exists (IGNORE strategy).
     * This provides atomic deduplication.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNotExists(state: NagStateEntity): Long

    /**
     * Update an existing nag state.
     */
    @Update
    suspend fun update(state: NagStateEntity)

    /**
     * Mark a nag as completed.
     */
    @Query("UPDATE nag_states SET status = 'COMPLETED' WHERE occurrenceKey = :key")
    suspend fun markCompleted(key: String)

    /**
     * Mark a nag as expired.
     */
    @Query("UPDATE nag_states SET status = 'EXPIRED' WHERE occurrenceKey = :key")
    suspend fun markExpired(key: String)

    /**
     * Mark a nag as cancelled (user cancelled from app).
     */
    @Query("UPDATE nag_states SET status = 'CANCELLED' WHERE occurrenceKey = :key")
    suspend fun markCancelled(key: String)

    /**
     * Update snooze status.
     */
    @Query("UPDATE nag_states SET status = 'SNOOZED', snoozedUntil = :until, snoozeCount = snoozeCount + 1 WHERE occurrenceKey = :key")
    suspend fun snooze(key: String, until: Long)

    /**
     * Clear snooze and set back to active.
     */
    @Query("UPDATE nag_states SET status = 'ACTIVE', snoozedUntil = NULL WHERE occurrenceKey = :key")
    suspend fun clearSnooze(key: String)

    /**
     * Increment nag count and update last nag time.
     */
    @Query("UPDATE nag_states SET nagCount = nagCount + 1, lastNagAt = :lastNagAt WHERE occurrenceKey = :key")
    suspend fun incrementNag(key: String, lastNagAt: Long)

    /**
     * Delete old completed/expired entries to prevent database bloat.
     * Keeps entries from the last 24 hours.
     */
    @Query("DELETE FROM nag_states WHERE status IN ('COMPLETED', 'EXPIRED', 'CANCELLED') AND triggeredAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    /**
     * Get all nags for a specific rule (for debugging).
     */
    @Query("SELECT * FROM nag_states WHERE ruleId = :ruleId ORDER BY scheduledTime DESC")
    suspend fun getByRuleId(ruleId: String): List<NagStateEntity>

    /**
     * Get all finished nags (completed, cancelled, or expired) since a specific time.
     * Used to filter out completed/abandoned/expired items from today's schedule.
     */
    @Query("SELECT * FROM nag_states WHERE status IN ('COMPLETED', 'CANCELLED', 'EXPIRED') AND triggeredAt >= :since")
    suspend fun getCompletedOrCancelledSince(since: Long): List<NagStateEntity>

}
