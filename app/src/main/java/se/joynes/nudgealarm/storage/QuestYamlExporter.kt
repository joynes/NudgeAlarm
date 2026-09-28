package se.joynes.nudgealarm.storage

import se.joynes.nudgealarm.database.ReminderEntity

/** Creates an editable, AI-friendly quest list that can be imported again. */
object QuestYamlExporter {
    fun export(reminders: List<ReminderEntity>, name: String = "Selected quests"): String = buildString {
        appendLine("# NudgeAlarm quest export")
        appendLine("# Ask an AI to edit, add, remove, or reorder entries in this file.")
        appendLine("# Keep the 'reminders:' structure and unique ids, then import the YAML into NudgeAlarm.")
        appendLine("# List order is the order shown in Quest Editor.")
        appendLine("# Name: ${commentText(name)}")
        appendLine("# Quests: ${reminders.size}")
        appendLine()
        appendLine("reminders:")
        reminders.forEach { reminder ->
            appendLine("  - id: ${yamlString(reminder.id)}")
            appendLine("    title: ${yamlString(reminder.title)}")
            appendLine("    schedule: ${yamlString(reminder.schedule)}")
            appendLine("    nag_interval: ${reminder.nagIntervalMinutes}m")
            appendLine("    max_nags: ${reminder.maxNags}")
            appendLine("    sticky: ${reminder.sticky}")
            appendLine("    enabled: ${reminder.enabled}")
            reminder.placeId?.let { appendLine("    place_id: ${yamlString(it)}") }
            appendLine("    sound: alarm")
            appendLine("    vibration: strong")
            appendLine("    snooze_options:")
            appendLine("      - 5m")
            appendLine("      - 15m")
            appendLine()
        }
    }

    private fun yamlString(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }

    private fun commentText(value: String): String = value.replace("\n", " ").replace("\r", " ")
}
