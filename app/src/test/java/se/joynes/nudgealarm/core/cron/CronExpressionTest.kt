package se.joynes.nudgealarm.core.cron

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CronExpressionTest {

    @Test
    fun parseValid5FieldExpression() {
        val cron = CronExpression.parse("30 7 * * *")
        assertTrue(cron.minute is CronField.Single)
        assertTrue(cron.hour is CronField.Single)
        assertTrue(cron.dayOfMonth is CronField.Any)
        assertTrue(cron.month is CronField.Any)
        assertTrue(cron.dayOfWeek is CronField.Any)
    }

    @Test
    fun parseWeekdayExpression() {
        val cron = CronExpression.parse("0 9 * * 1-5")
        assertTrue(cron.minute is CronField.Single)
        assertTrue(cron.hour is CronField.Single)
        assertTrue(cron.dayOfMonth is CronField.Any)
        assertTrue(cron.month is CronField.Any)
        assertTrue(cron.dayOfWeek is CronField.Range)
    }

    @Test
    fun parseWeekendExpression() {
        val cron = CronExpression.parse("0 10 * * 0,6")
        assertTrue(cron.dayOfWeek is CronField.List)
        val list = cron.dayOfWeek as CronField.List
        assertEquals(listOf(0, 6), list.values)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseWithWrongNumberOfFieldsThrows() {
        CronExpression.parse("30 7 * *") // Only 4 fields
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseWithTooManyFieldsThrows() {
        CronExpression.parse("0 30 7 * * *") // 6 fields
    }

    @Test
    fun nextTriggerTimeForEveryMinute() {
        val cron = CronExpression.parse("* * * * *")
        val now = System.currentTimeMillis()
        val next = cron.nextTriggerTime(now)

        // Should be next minute
        assertTrue(next > now)
        assertTrue(next <= now + 60_000)
    }

    @Test
    fun nextTriggerTimeForSpecificTimeDaily() {
        val cron = CronExpression.parse("30 7 * * *") // 7:30 daily

        // Create a time at 7:00 AM
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val next = cron.nextTriggerTime(cal.timeInMillis)

        // Should be 7:30 same day
        val nextCal = Calendar.getInstance().apply { timeInMillis = next }
        assertEquals(7, nextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, nextCal.get(Calendar.MINUTE))
    }

    @Test
    fun nextTriggerTimeRollsToNextDayIfPastTimeToday() {
        val cron = CronExpression.parse("30 7 * * *") // 7:30 daily

        // Create a time at 8:00 AM (after 7:30)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val next = cron.nextTriggerTime(cal.timeInMillis)

        // Should be 7:30 next day
        val nextCal = Calendar.getInstance().apply { timeInMillis = next }
        assertEquals(7, nextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, nextCal.get(Calendar.MINUTE))
        assertEquals(cal.get(Calendar.DAY_OF_YEAR) + 1, nextCal.get(Calendar.DAY_OF_YEAR))
    }

    @Test
    fun nextTriggerTimeRespectsWeekdayConstraint() {
        val cron = CronExpression.parse("0 9 * * 1-5") // 9:00 Mon-Fri

        // Find a Saturday
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val next = cron.nextTriggerTime(cal.timeInMillis)
        val nextCal = Calendar.getInstance().apply { timeInMillis = next }

        // Should be Monday (skipping Saturday and Sunday)
        assertEquals(Calendar.MONDAY, nextCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(9, nextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, nextCal.get(Calendar.MINUTE))
    }

    @Test
    fun nextTriggerTimeRespectsWeekendConstraint() {
        val cron = CronExpression.parse("0 10 * * 0,6") // 10:00 Sat,Sun

        // Find a Monday
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val next = cron.nextTriggerTime(cal.timeInMillis)
        val nextCal = Calendar.getInstance().apply { timeInMillis = next }

        // Should be Saturday
        assertEquals(Calendar.SATURDAY, nextCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(10, nextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, nextCal.get(Calendar.MINUTE))
    }

    @Test
    fun dayMatchingBothAsteriskMatchesEveryDay() {
        val cron = CronExpression.parse("0 12 * * *") // noon every day

        // Test multiple days of week
        for (dayOffset in 0..6) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 11)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_MONTH, dayOffset)
            }

            val next = cron.nextTriggerTime(cal.timeInMillis)
            val nextCal = Calendar.getInstance().apply { timeInMillis = next }

            // Should be same day at noon
            assertEquals(cal.get(Calendar.DAY_OF_YEAR), nextCal.get(Calendar.DAY_OF_YEAR))
            assertEquals(12, nextCal.get(Calendar.HOUR_OF_DAY))
        }
    }

    @Test
    fun dayMatchingSpecificDayOfWeekWithAsteriskDayOfMonth() {
        // This tests the fix for the bug where weekend rules triggered on weekdays
        val cron = CronExpression.parse("0 10 * * 0,6") // 10:00 weekends only

        // Find a Wednesday
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.WEDNESDAY) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val next = cron.nextTriggerTime(cal.timeInMillis)
        val nextCal = Calendar.getInstance().apply { timeInMillis = next }

        // Should NOT be Wednesday - should be Saturday
        assertNotEquals(Calendar.WEDNESDAY, nextCal.get(Calendar.DAY_OF_WEEK))
        assertTrue(
            nextCal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
            nextCal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        )
    }

    @Test
    fun multipleTriggersSameDayAtDifferentTimes() {
        // Simulate multiple reminders at different times
        val times = listOf("30 7 * * *", "0 12 * * *", "30 19 * * *")

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val triggers = times.map { schedule ->
            val cron = CronExpression.parse(schedule)
            cron.nextTriggerTime(cal.timeInMillis)
        }

        // Verify they're in order and all same day
        assertTrue(triggers[0] < triggers[1])
        assertTrue(triggers[1] < triggers[2])

        val day = Calendar.getInstance().apply { timeInMillis = triggers[0] }.get(Calendar.DAY_OF_YEAR)
        triggers.forEach { trigger ->
            assertEquals(day, Calendar.getInstance().apply { timeInMillis = trigger }.get(Calendar.DAY_OF_YEAR))
        }
    }
}
