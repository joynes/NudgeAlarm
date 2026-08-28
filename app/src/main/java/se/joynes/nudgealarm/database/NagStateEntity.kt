package se.joynes.nudgealarm.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single occurrence of a reminder that has been triggered.
 * The occurrenceKey is unique per (ruleId, scheduledTime) to prevent duplicates.
 */
@Entity(tableName = "nag_states")
data class NagStateEntity(
    @PrimaryKey
    val occurrenceKey: String,      // "ruleId@scheduledTime" - unique identifier
    val ruleId: String,             // The rule that triggered this
    val scheduledTime: Long,        // When it was scheduled to fire (rounded to minute)
    val title: String,              // Display title for notifications
    val triggeredAt: Long,          // When it actually fired
    val nagCount: Int,              // Current nag count
    val maxNags: Int,               // Maximum nags allowed
    val nagIntervalMs: Long,        // Milliseconds between nags
    val status: String,             // NagStatus as string
    val snoozedUntil: Long?,        // When snooze ends (null if not snoozed)
    val lastNagAt: Long?,           // When last nag was sent
    val snoozeCount: Int = 0        // How many times this occurrence has been snoozed
) {
    fun hasReachedNagLimit(sticky: Boolean): Boolean {
        return !sticky && nagCount >= maxNags
    }

    companion object {
        fun createOccurrenceKey(ruleId: String, scheduledTime: Long): String {
            return "$ruleId@$scheduledTime"
        }

        fun parseOccurrenceKey(key: String): Pair<String, Long>? {
            val parts = key.split("@")
            if (parts.size != 2) return null
            val ruleId = parts[0]
            val scheduledTime = parts[1].toLongOrNull() ?: return null
            return ruleId to scheduledTime
        }
    }
}
