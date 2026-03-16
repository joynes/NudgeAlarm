package org.nudgealarm.app.core.config

import org.yaml.snakeyaml.Yaml
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

object ConfigParser {

    fun parse(yamlContent: String): Result<AppConfig> = runCatching {
        val yaml = Yaml()
        val data = yaml.load<Map<String, Any>>(yamlContent)

        @Suppress("UNCHECKED_CAST")
        val remindersData = data["reminders"] as? List<Map<String, Any>>
            ?: throw IllegalArgumentException("Missing 'reminders' key in YAML")

        val reminders = remindersData.map { parseReminder(it) }
        AppConfig(reminders = reminders)
    }

    private fun parseReminder(data: Map<String, Any>): ReminderConfig {
        val id = data["id"] as? String
            ?: throw IllegalArgumentException("Reminder missing 'id'")
        val title = data["title"] as? String
            ?: throw IllegalArgumentException("Reminder '$id' missing 'title'")
        val schedule = data["schedule"] as? String
            ?: throw IllegalArgumentException("Reminder '$id' missing 'schedule'")

        val nagInterval = (data["nag_interval"] as? String)?.let { parseDuration(it) }
            ?: 5.minutes
        val maxNags = (data["max_nags"] as? Number)?.toInt() ?: 100
        val sound = data["sound"] as? String ?: "alarm"
        val vibration = data["vibration"] as? String ?: "strong"

        @Suppress("UNCHECKED_CAST")
        val snoozeOptions = (data["snooze_options"] as? List<String>)
            ?.map { parseDuration(it) }
            ?: listOf(5.minutes, 15.minutes)

        return ReminderConfig(
            id = id,
            title = title,
            schedule = schedule,
            nagInterval = nagInterval,
            maxNags = maxNags,
            sound = sound,
            vibration = vibration,
            snoozeOptions = snoozeOptions
        )
    }

    private fun parseDuration(durationStr: String): Duration {
        val trimmed = durationStr.trim().lowercase()
        return when {
            trimmed.endsWith("m") -> {
                val value = trimmed.dropLast(1).toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid duration: $durationStr")
                value.minutes
            }
            trimmed.endsWith("h") -> {
                val value = trimmed.dropLast(1).toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid duration: $durationStr")
                value.hours
            }
            else -> throw IllegalArgumentException("Unknown duration format: $durationStr")
        }
    }
}
