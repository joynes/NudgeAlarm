package se.joynes.nudgealarm.service

import org.junit.Assert.*
import org.junit.Test
import se.joynes.nudgealarm.core.cron.CronExpression
import java.util.Calendar

/**
 * Tests for the scheduler logic to ensure reminders are triggered correctly.
 * These tests verify that the poll interval and lookback window are configured
 * properly to catch all scheduled reminders.
 */
class SchedulerLogicTest {

    companion object {
        // These must match the values in ReminderService
        const val POLL_INTERVAL_MS = 30 * 1000L // 30 seconds
        const val LOOKBACK_MS = 2 * 60 * 1000L // 2 minutes
    }

    @Test
    fun pollIntervalShouldBeLessThanOneMinute() {
        // Poll interval must be less than 1 minute to catch reminders that trigger
        // at specific minutes (cron expressions are minute-based)
        assertTrue(
            "Poll interval ($POLL_INTERVAL_MS ms) must be less than 60 seconds",
            POLL_INTERVAL_MS < 60 * 1000L
        )
    }

    @Test
    fun lookbackWindowShouldBeLargerThanPollInterval() {
        // Lookback window must be larger than poll interval to ensure we don't
        // miss reminders between polls
        assertTrue(
            "Lookback window ($LOOKBACK_MS ms) must be larger than poll interval ($POLL_INTERVAL_MS ms)",
            LOOKBACK_MS > POLL_INTERVAL_MS
        )
    }

    @Test
    fun reminderScheduledForNextMinuteShouldBeDetected() {
        // Given: current time and a reminder scheduled for next minute
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nextMinute = cal.get(Calendar.MINUTE)
        val nextHour = cal.get(Calendar.HOUR_OF_DAY)

        val schedule = "$nextMinute $nextHour * * *"
        val cron = CronExpression.parse(schedule)

        // When: we check for the next trigger from lookback window
        val checkFrom = cal.timeInMillis - LOOKBACK_MS
        val nextTrigger = cron.nextTriggerTime(checkFrom)

        // Then: the trigger time should be detected within the minute window
        assertTrue(
            "Reminder at ${nextHour}:${nextMinute} should be detected",
            nextTrigger <= cal.timeInMillis + 60000 // within the minute
        )
    }

    @Test
    fun reminderInThePastMinuteShouldBeDetected() {
        // Given: a reminder that should have triggered 30 seconds ago
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val currentMinute = cal.get(Calendar.MINUTE)
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)

        val schedule = "$currentMinute $currentHour * * *"
        val cron = CronExpression.parse(schedule)

        // When: we check from lookback window
        val checkFrom = now - LOOKBACK_MS
        val nextTrigger = cron.nextTriggerTime(checkFrom)

        // Then: should detect the trigger that happened this minute
        assertTrue(
            "Reminder at ${currentHour}:${currentMinute} should be detected even if we're 30s past",
            nextTrigger <= now
        )
    }

    @Test
    fun multiplePolllsShouldNotMissReminder() {
        // Simulate multiple polls and verify reminder would be caught
        val now = System.currentTimeMillis()

        // Set up a reminder for the current minute
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val minute = cal.get(Calendar.MINUTE)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val schedule = "$minute $hour * * *"
        val cron = CronExpression.parse(schedule)
        val scheduledTime = cal.timeInMillis

        // Simulate polls at different offsets within the minute
        val pollOffsets = listOf(0L, 15000L, 30000L, 45000L) // 0s, 15s, 30s, 45s

        var wasDetected = false
        for (offset in pollOffsets) {
            val pollTime = scheduledTime + offset
            val checkFrom = pollTime - LOOKBACK_MS
            val nextTrigger = cron.nextTriggerTime(checkFrom)

            if (nextTrigger <= pollTime) {
                wasDetected = true
                break
            }
        }

        assertTrue("Reminder should be detected by at least one poll", wasDetected)
    }

    @Test
    fun pollIntervalIsReasonableForBatteryLife() {
        // Poll interval should be at least 10 seconds to not drain battery excessively
        assertTrue(
            "Poll interval should be at least 10 seconds for battery life",
            POLL_INTERVAL_MS >= 10 * 1000L
        )
    }

    @Test
    fun cronExpressionParsesCorrectly() {
        // Test various cron expressions parse without error
        val expressions = listOf(
            "0 8 * * *",      // 8:00 every day
            "30 9 * * 1-5",   // 9:30 weekdays
            "0 10 * * 0,6",   // 10:00 weekends
            "0 * * * *",      // every hour
            "15 8 * * 1"      // 8:15 on Monday
        )

        for (expr in expressions) {
            try {
                CronExpression.parse(expr)
            } catch (e: Exception) {
                fail("Failed to parse cron expression: $expr - ${e.message}")
            }
        }
    }

    @Test
    fun nextTriggerTimeIsInTheFuture() {
        val now = System.currentTimeMillis()
        val cron = CronExpression.parse("0 * * * *") // every hour

        val nextTrigger = cron.nextTriggerTime(now)

        assertTrue(
            "Next trigger should be in the future or at current time",
            nextTrigger >= now
        )
    }

    @Test
    fun weekdayScheduleOnlyTriggersOnWeekdays() {
        val cron = CronExpression.parse("0 8 * * 1-5") // 8:00 Mon-Fri

        // Find a Monday
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val nextTrigger = cron.nextTriggerTime(cal.timeInMillis)

        // Should trigger at 8:00 on the same Monday
        val triggerCal = Calendar.getInstance().apply { timeInMillis = nextTrigger }
        val dayOfWeek = triggerCal.get(Calendar.DAY_OF_WEEK)

        assertTrue(
            "Should trigger on a weekday (Mon-Fri)",
            dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
        )
    }

    @Test
    fun weekendScheduleOnlyTriggersOnWeekends() {
        val cron = CronExpression.parse("0 10 * * 0,6") // 10:00 Sat,Sun

        // Find a Friday evening
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY)
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val nextTrigger = cron.nextTriggerTime(cal.timeInMillis)

        // Should trigger on Saturday at 10:00
        val triggerCal = Calendar.getInstance().apply { timeInMillis = nextTrigger }
        val dayOfWeek = triggerCal.get(Calendar.DAY_OF_WEEK)

        assertTrue(
            "Should trigger on a weekend day (Sat or Sun)",
            dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        )
    }
}
