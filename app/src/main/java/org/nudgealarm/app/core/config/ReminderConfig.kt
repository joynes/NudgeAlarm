package org.nudgealarm.app.core.config

import org.nudgealarm.app.core.cron.CronExpression
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class ReminderConfig(
    val id: String,
    val title: String,
    val schedule: String,
    val nagInterval: Duration = 5.minutes,
    val maxNags: Int = 100,
    val sound: String = "alarm",
    val vibration: String = "strong",
    val snoozeOptions: List<Duration> = listOf(5.minutes, 15.minutes)
)

/**
 * Returns the approximate number of days between two consecutive occurrences.
 * One-time events return Double.MAX_VALUE (never stale).
 */
fun ReminderConfig.approximateIntervalDays(): Double {
    if (schedule.startsWith("once:")) return Double.MAX_VALUE
    return try {
        val cron = CronExpression.parse(schedule)
        val t1 = cron.nextTriggerTime(System.currentTimeMillis())
        val t2 = cron.nextTriggerTime(t1)
        (t2 - t1).toDouble() / (24.0 * 60 * 60 * 1000)
    } catch (e: Exception) {
        1.0
    }
}
