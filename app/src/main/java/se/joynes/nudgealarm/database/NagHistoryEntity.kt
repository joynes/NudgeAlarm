package se.joynes.nudgealarm.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores historical data for all reminder completions.
 * Unlike NagStateEntity, these records are never deleted automatically.
 */
@Entity(tableName = "nag_history")
data class NagHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ruleId: String,
    val title: String,
    val scheduledTime: Long,
    val triggeredAt: Long,
    val completedAt: Long,
    val outcome: String,        // COMPLETED, EXPIRED, CANCELLED
    val nagCount: Int,
    val maxNags: Int,
    val responseTimeMs: Long    // completedAt - triggeredAt
)
