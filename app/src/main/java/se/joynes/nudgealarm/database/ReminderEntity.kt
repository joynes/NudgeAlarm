package se.joynes.nudgealarm.database

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import se.joynes.nudgealarm.core.config.ReminderConfig
import kotlin.time.Duration.Companion.minutes

/**
 * Database entity for storing reminder configurations.
 * Replaces YAML-based configuration with editable database storage.
 */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val schedule: String,              // Cron expression
    val nagIntervalMinutes: Int = 5,
    val maxNags: Int = 100,
    @ColumnInfo(defaultValue = "0")
    val sticky: Boolean = false,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toReminderConfig(): ReminderConfig {
        return ReminderConfig(
            id = id,
            title = title,
            schedule = schedule,
            nagInterval = nagIntervalMinutes.minutes,
            maxNags = maxNags,
            sticky = sticky
        )
    }

    companion object {
        fun fromReminderConfig(config: ReminderConfig): ReminderEntity {
            return ReminderEntity(
                id = config.id,
                title = config.title,
                schedule = config.schedule,
                nagIntervalMinutes = config.nagInterval.inWholeMinutes.toInt(),
                maxNags = config.maxNags,
                sticky = config.sticky
            )
        }
    }
}
