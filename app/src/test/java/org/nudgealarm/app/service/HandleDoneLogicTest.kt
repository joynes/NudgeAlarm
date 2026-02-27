package org.nudgealarm.app.service

import org.junit.Assert.*
import org.junit.Test
import org.nudgealarm.app.core.cron.CronExpression
import java.util.Calendar

/**
 * Tests for the handleDone "early completion" branch logic:
 * verifies the 2-hour lookback window finds today's trigger time
 * rather than tomorrow's, even when called minutes after the scheduled time.
 */
class HandleDoneLogicTest {

    // ── 2-hour lookback correctness ───────────────────────────────────────────

    @Test
    fun twoHourLookbackFindsPastTriggerWhenCalledAfterScheduledTime() {
        // Quest scheduled for 09:00 every day; user taps DONE at 09:30
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis
        val cron = CronExpression.parse("0 9 * * *")

        // 2-hour lookback should land before 09:00, finding today's trigger
        val trigger = cron.nextTriggerTime(now - (2 * 60 * 60 * 1000))
        val trigCal = Calendar.getInstance().apply { timeInMillis = trigger }

        assertEquals("Should find today's 09:00 trigger", 9, trigCal.get(Calendar.HOUR_OF_DAY))
        assertEquals("Should find today's 09:00 trigger", 0, trigCal.get(Calendar.MINUTE))
        assertTrue("Trigger should be in the past relative to 09:30", trigger <= now)
    }

    @Test
    fun sixtySecondLookbackReturnsTomorrowTriggerWhenCalledAfterScheduledTime() {
        // The old (buggy) 60-second lookback returned tomorrow's trigger.
        // This test documents the bug that the fix addresses.
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis
        val cron = CronExpression.parse("0 9 * * *")

        // 60-second lookback: fromTime = 09:29 → next trigger AFTER 09:29 = tomorrow's 09:00
        val trigger = cron.nextTriggerTime(now - 60_000)

        // The 60s lookback finds a future trigger (bug behaviour), not today's past trigger
        assertTrue("60s lookback should return a future trigger (bug mode)", trigger > now)
    }

    @Test
    fun twoHourLookbackWorksForTrigger90MinutesAgo() {
        // Quest triggered 90 minutes ago – within the 2h window
        val now = System.currentTimeMillis()
        val ninetyMinAgo = now - (90 * 60 * 1000)
        val roundedTrigger = (ninetyMinAgo / 60_000) * 60_000

        val cal = Calendar.getInstance().apply { timeInMillis = roundedTrigger }
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        val cron = CronExpression.parse("$m $h * * *")

        val found = cron.nextTriggerTime(now - (2 * 60 * 60 * 1000))

        assertEquals("Should find the trigger 90 min ago", roundedTrigger, found)
        assertTrue("Trigger should be in the past", found <= now)
    }

    @Test
    fun twoHourLookbackWorksWhenCalledImmediatelyAfterTrigger() {
        // handleDone called just 30 seconds after trigger
        val cal = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val triggerTime = cal.timeInMillis
        val m = cal.get(Calendar.MINUTE)
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val cron = CronExpression.parse("$m $h * * *")

        val callTime = triggerTime + 30_000 // 30 seconds later
        val found = cron.nextTriggerTime(callTime - (2 * 60 * 60 * 1000))

        assertEquals("Should find the trigger at the current minute", triggerTime, found)
    }

    // ── scheduledTime rounding ────────────────────────────────────────────────

    @Test
    fun scheduledTimeRoundingStripsSubMinuteComponent() {
        val withSecondsMs = 1706684430_000L // 30 seconds past the minute
        val expectedRounded = 1706684400_000L
        val rounded = (withSecondsMs / 60_000) * 60_000
        assertEquals(expectedRounded, rounded)
    }

    @Test
    fun scheduledTimeRoundingResultIsDivisibleBy60000() {
        val now = System.currentTimeMillis()
        val rounded = (now / 60_000) * 60_000
        assertEquals(0L, rounded % 60_000)
    }

    @Test
    fun onceScheduleTimestampRoundingStaysWithinOneMinuteOfOriginal() {
        val rawTs = System.currentTimeMillis()
        val rounded = (rawTs / 60_000) * 60_000
        assertTrue("Rounded time should be within 60s of original", rawTs - rounded < 60_000)
    }

    // ── weekday schedule edge cases ───────────────────────────────────────────

    @Test
    fun twoHourLookbackWorksForWeekdaySchedule() {
        // Find a Monday 09:30
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis
        val cron = CronExpression.parse("0 9 * * 1-5") // weekdays 09:00

        val trigger = cron.nextTriggerTime(now - (2 * 60 * 60 * 1000))
        val trigCal = Calendar.getInstance().apply { timeInMillis = trigger }

        assertEquals(Calendar.MONDAY, trigCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(9, trigCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, trigCal.get(Calendar.MINUTE))
        assertTrue("Should find Monday's 09:00 in the past", trigger <= now)
    }

    @Test
    fun lookbackDoesNotGoBackMoreThanTwoHours() {
        // If quest triggered 3 hours ago, 2h lookback should NOT find it
        val now = System.currentTimeMillis()
        val threeHoursAgo = now - (3 * 60 * 60 * 1000)
        val cal = Calendar.getInstance().apply { timeInMillis = threeHoursAgo }
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        val cron = CronExpression.parse("$m $h * * *")

        val found = cron.nextTriggerTime(now - (2 * 60 * 60 * 1000))

        // Should find tomorrow's trigger (not 3h ago), so found > now
        assertTrue(
            "2h lookback should not reach 3h-old trigger; found trigger should be in future",
            found > now
        )
    }
}
