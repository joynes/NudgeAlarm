package org.nudgealarm.app.core.cron

import java.util.Calendar
import java.util.TimeZone

data class CronExpression(
    val minute: CronField,
    val hour: CronField,
    val dayOfMonth: CronField,
    val month: CronField,
    val dayOfWeek: CronField
) {
    fun nextTriggerTime(fromTimeMillis: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeZone = TimeZone.getDefault()
            timeInMillis = fromTimeMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, 1)
        }

        val maxIterations = 366 * 24 * 60
        repeat(maxIterations) {
            val calMinute = calendar.get(Calendar.MINUTE)
            val calHour = calendar.get(Calendar.HOUR_OF_DAY)
            val calDayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            val calMonth = calendar.get(Calendar.MONTH) + 1
            val calDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK).let {
                if (it == Calendar.SUNDAY) 0 else it - 1
            }

            // Correct cron day matching logic:
            // - If both dayOfMonth and dayOfWeek are *, match every day
            // - If dayOfMonth is * but dayOfWeek is specific, only check dayOfWeek
            // - If dayOfWeek is * but dayOfMonth is specific, only check dayOfMonth
            // - If both are specific, match on either (OR) - standard cron behavior
            val dayMatches = when {
                dayOfMonth is CronField.Any && dayOfWeek is CronField.Any -> true
                dayOfMonth is CronField.Any -> dayOfWeek.matches(calDayOfWeek)
                dayOfWeek is CronField.Any -> dayOfMonth.matches(calDayOfMonth)
                else -> dayOfMonth.matches(calDayOfMonth) || dayOfWeek.matches(calDayOfWeek)
            }

            if (month.matches(calMonth) &&
                dayMatches &&
                hour.matches(calHour) &&
                minute.matches(calMinute)
            ) {
                return calendar.timeInMillis
            }

            calendar.add(Calendar.MINUTE, 1)
        }

        throw IllegalStateException("Could not find next trigger time within a year")
    }

    companion object {
        fun parse(expression: String): CronExpression {
            val parts = expression.trim().split(Regex("\\s+"))
            if (parts.size != 5) {
                throw IllegalArgumentException(
                    "Invalid cron expression: expected 5 fields, got ${parts.size}"
                )
            }

            return CronExpression(
                minute = CronField.parse(parts[0], 0, 59),
                hour = CronField.parse(parts[1], 0, 23),
                dayOfMonth = CronField.parse(parts[2], 1, 31),
                month = CronField.parse(parts[3], 1, 12),
                dayOfWeek = CronField.parse(parts[4], 0, 6)
            )
        }
    }
}
