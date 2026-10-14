package se.joynes.nudgealarm.ui

import org.junit.Assert.*
import org.junit.Test

class PauseCountdownTest {
    @Test fun formatsMinutesHoursAndDaysWithoutZeroUnits() {
        assertEquals("1m left", pauseRemainingLabel(60_000, 0))
        assertEquals("30m left", pauseRemainingLabel(30 * 60_000L, 0))
        assertEquals("1h left", pauseRemainingLabel(60 * 60_000L, 0))
        assertEquals("2h 15m left", pauseRemainingLabel(135 * 60_000L, 0))
        assertEquals("1d 2h 15m left", pauseRemainingLabel((1440 + 135) * 60_000L, 0))
    }
    @Test fun finalMinuteShowsSecondsAndRoundsUp() {
        assertEquals("59s left", pauseRemainingLabel(59_000, 0))
        assertEquals("1s left", pauseRemainingLabel(1, 0))
        assertEquals("2m left", pauseRemainingLabel(60_001, 0))
    }
    @Test fun expiredCountdownNeverShowsNegativeTime() {
        assertEquals("Resuming…", pauseRemainingLabel(100, 100))
        assertEquals("Resuming…", pauseRemainingLabel(100, 200))
    }
    @Test fun refreshesOnMinuteBoundariesAndOnlySecondsInFinalMinute() {
        assertEquals(60_000L, countdownRefreshDelay(listOf(120_000), 0))
        assertEquals(15_000L, countdownRefreshDelay(listOf(135_000), 0))
        assertEquals(1000L, countdownRefreshDelay(listOf(60_000), 0))
        assertEquals(300L, countdownRefreshDelay(listOf(300), 0))
    }
    @Test fun oneClockHandlesMultiplePausesAndStopsWhenNoneRemain() {
        assertEquals(1000L, countdownRefreshDelay(listOf(130_000, 30_000, -10), 0))
        assertNull(countdownRefreshDelay(emptyList(), 0))
        assertNull(countdownRefreshDelay(listOf(50, 100), 100))
    }
}
