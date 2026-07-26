package se.joynes.nudgealarm.database

import kotlinx.coroutines.flow.Flow
import se.joynes.nudgealarm.core.config.ReminderConfig
import kotlin.time.Duration

/**
 * Repository for managing nag states with atomic deduplication.
 * Provides a clean API for the service to interact with persistent storage.
 */
class NagRepository(private val dao: NagStateDao) {

    /**
     * Try to fire a reminder for a specific scheduled time.
     * Returns true if this is a new occurrence (inserted successfully),
     * false if it already exists (deduplication).
     *
     * This is atomic - uses INSERT ... ON CONFLICT IGNORE.
     */
    suspend fun tryFire(
        rule: ReminderConfig,
        scheduledTime: Long
    ): Pair<Boolean, String> {
        val occurrenceKey = NagStateEntity.createOccurrenceKey(rule.id, scheduledTime)

        val entity = NagStateEntity(
            occurrenceKey = occurrenceKey,
            ruleId = rule.id,
            scheduledTime = scheduledTime,
            title = rule.title,
            triggeredAt = System.currentTimeMillis(),
            nagCount = 1,
            maxNags = rule.maxNags,
            nagIntervalMs = rule.nagInterval.inWholeMilliseconds,
            status = NagStatus.ACTIVE.name,
            snoozedUntil = null,
            lastNagAt = System.currentTimeMillis()
        )

        val result = dao.insertIfNotExists(entity)
        val isNew = result != -1L

        return isNew to occurrenceKey
    }

    /**
     * Check if an occurrence has already been fired.
     */
    suspend fun hasAlreadyFired(ruleId: String, scheduledTime: Long): Boolean {
        val key = NagStateEntity.createOccurrenceKey(ruleId, scheduledTime)
        return dao.existsByKey(key) > 0
    }

    /**
     * Get the current state of a nag by its occurrence key.
     */
    suspend fun getByKey(occurrenceKey: String): NagStateEntity? {
        return dao.getByKey(occurrenceKey)
    }

    /**
     * Get all active nags as a Flow for reactive updates.
     */
    fun getActiveNagsFlow(): Flow<List<NagStateEntity>> {
        return dao.getActiveNagsFlow()
    }

    /**
     * Get all active nags (one-time read).
     */
    suspend fun getActiveNags(): List<NagStateEntity> {
        return dao.getActiveNags()
    }

    /**
     * Increment the nag count for an occurrence.
     */
    suspend fun incrementNag(occurrenceKey: String) {
        dao.incrementNag(occurrenceKey, System.currentTimeMillis())
    }

    /**
     * Mark a nag as completed (user tapped DONE).
     */
    suspend fun markDone(occurrenceKey: String) {
        dao.markCompleted(occurrenceKey)
    }

    /**
     * Mark all active nags as completed by ruleId.
     * Returns the list of completed NagStateEntities (for history recording).
     */
    suspend fun markDoneByRuleId(ruleId: String): NagStateEntity? {
        val activeNags = dao.getActiveNags()
        val nags = activeNags.filter { it.ruleId == ruleId }
        for (nag in nags) {
            dao.markCompleted(nag.occurrenceKey)
        }
        return nags.firstOrNull()
    }

    /**
     * Mark a nag as expired (max nags reached).
     */
    suspend fun markExpired(occurrenceKey: String) {
        dao.markExpired(occurrenceKey)
    }

    /**
     * Mark a nag as cancelled (user cancelled from app).
     */
    suspend fun markCancelled(occurrenceKey: String) {
        dao.markCancelled(occurrenceKey)
    }

    /**
     * Mark a nag as cancelled by ruleId (finds active occurrence for that rule).
     * Returns the NagStateEntity if found (for history recording).
     */
    suspend fun markCancelledByRuleId(ruleId: String): NagStateEntity? {
        val activeNags = dao.getActiveNags()
        val nags = activeNags.filter { it.ruleId == ruleId }
        for (nag in nags) {
            dao.markCancelled(nag.occurrenceKey)
        }
        return nags.firstOrNull()
    }

    /**
     * Snooze a nag until a specific time.
     */
    suspend fun snooze(occurrenceKey: String, until: Long) {
        dao.snooze(occurrenceKey, until)
    }

    /**
     * Snooze by ruleId (finds active occurrence for that rule).
     * Returns the occurrence key if found.
     */
    suspend fun snoozeByRuleId(ruleId: String, duration: Duration): String? {
        val activeNags = dao.getActiveNags()
        val nag = activeNags.find { it.ruleId == ruleId }
        if (nag != null) {
            val until = System.currentTimeMillis() + duration.inWholeMilliseconds
            dao.snooze(nag.occurrenceKey, until)
            return nag.occurrenceKey
        }
        return null
    }

    /**
     * Clear snooze status and resume nagging.
     */
    suspend fun clearSnooze(occurrenceKey: String) {
        dao.clearSnooze(occurrenceKey)
    }

    /**
     * Check if a nag is currently snoozed.
     */
    suspend fun isSnoozed(occurrenceKey: String): Boolean {
        val nag = dao.getByKey(occurrenceKey)
        return nag?.status == NagStatus.SNOOZED.name
    }

    /**
     * Check if a nag is done (completed or expired).
     */
    suspend fun isDone(occurrenceKey: String): Boolean {
        val nag = dao.getByKey(occurrenceKey)
        return nag?.status == NagStatus.COMPLETED.name || nag?.status == NagStatus.EXPIRED.name
    }

    /**
     * Check if a rule has any active (non-completed) occurrence.
     */
    suspend fun isRuleActive(ruleId: String): Boolean {
        val activeNags = dao.getActiveNags()
        return activeNags.any { it.ruleId == ruleId }
    }

    /**
     * Get the active occurrence key for a rule, if any.
     */
    suspend fun getActiveOccurrenceKeyForRule(ruleId: String): String? {
        val activeNags = dao.getActiveNags()
        return activeNags.find { it.ruleId == ruleId }?.occurrenceKey
    }

    /**
     * Get rule IDs that have been completed, cancelled, or expired today.
     * Used to filter out finished items from today's schedule.
     */
    suspend fun getCompletedOrCancelledRuleIdsToday(): Set<String> {
        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        return dao.getCompletedOrCancelledSince(startOfDay)
            .map { it.ruleId }
            .toSet()
    }

    /**
     * Create a CANCELLED nag state entry for a quest that hasn't triggered yet.
     * Used when abandoning a quest from the Quest Log before it fires.
     */
    suspend fun createCancelledEntry(ruleId: String, title: String) {
        val now = System.currentTimeMillis()
        val occurrenceKey = NagStateEntity.createOccurrenceKey(ruleId, now)
        val entity = NagStateEntity(
            occurrenceKey = occurrenceKey,
            ruleId = ruleId,
            scheduledTime = now,
            title = title,
            triggeredAt = now,
            nagCount = 0,
            maxNags = 0,
            nagIntervalMs = 0,
            status = NagStatus.CANCELLED.name,
            snoozedUntil = null,
            lastNagAt = null
        )
        dao.insertIfNotExists(entity)
    }

    /**
     * Clean up old completed/expired entries.
     * Call periodically to prevent database bloat.
     */
    suspend fun cleanup(keepDurationMs: Long = 24 * 60 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - keepDurationMs
        dao.deleteOlderThan(cutoff)
    }

    /**
     * Clear all nag states (for testing or full reset).
     */
    suspend fun clearAll() {
        dao.deleteAll()
    }
}
