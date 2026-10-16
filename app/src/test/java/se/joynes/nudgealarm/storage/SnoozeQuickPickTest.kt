package se.joynes.nudgealarm.storage

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class SnoozeQuickPickTest {
    private val zone = ZoneId.of("Europe/Stockholm")
    private val now = ZonedDateTime.of(2026, 10, 16, 10, 30, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun combinesBothKindsInOrderOfWhenTheyResume() {
        val clocks = listOf(SnoozeClockShortcut("work", "After work", 17, 0), SnoozeClockShortcut("lunch", "Lunch", 12, 0))
        val picks = snoozeQuickPicks(listOf(30, 120, 1440), clocks, now, zone)
        assertEquals(5, picks.size)
        assertEquals(listOf(30, null, 120, null, 1440), picks.map { (it as? SnoozeQuickPick.Duration)?.minutes })
        assertEquals(listOf("lunch", "work"), picks.filterIsInstance<SnoozeQuickPick.Clock>().map { it.shortcut.id })
        assertTrue(picks.zipWithNext().all { (a, b) -> a.resumeAt <= b.resumeAt })
    }

    @Test fun passedClockTimesComeTomorrowAndAreNotLostBehindDurationLimit() {
        val past = SnoozeClockShortcut("breakfast", "Breakfast", 8, 0)
        val picks = snoozeQuickPicks(DEFAULT_SNOOZE_PRESETS, listOf(past), now, zone)
        assertEquals(10, picks.size)
        val clock = picks.filterIsInstance<SnoozeQuickPick.Clock>().single()
        assertEquals(21 * 60L + 30, (clock.resumeAt - now) / 60_000L)
        assertEquals(9, picks.filterIsInstance<SnoozeQuickPick.Duration>().size)
    }

    @Test fun clockOnlyAndDurationOnlyConfigurationsWork() {
        assertTrue(snoozeQuickPicks(emptyList(), emptyList(), now, zone).isEmpty())
        assertEquals(1, snoozeQuickPicks(emptyList(), listOf(SnoozeClockShortcut("a", "Lunch", 12, 0)), now, zone).size)
        assertEquals(listOf(5, 30), snoozeQuickPicks(listOf(30, 5), emptyList(), now, zone).map { (it as SnoozeQuickPick.Duration).minutes })
    }
}
