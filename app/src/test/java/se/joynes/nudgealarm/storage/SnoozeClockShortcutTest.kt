package se.joynes.nudgealarm.storage

import java.time.ZonedDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SnoozeClockShortcutTest {
    private val zone = ZoneId.of("Europe/Stockholm")
    private fun at(value: String) = ZonedDateTime.parse("$value[Europe/Stockholm]").toInstant().toEpochMilli()

    @Test fun lunchLaterToday() {
        val now = at("2026-10-12T10:30:00+02:00")
        assertEquals(at("2026-10-12T12:00:00+02:00"), nextSnoozeClockTime(12, 0, now, zone))
        assertEquals(90, snoozeClockMinutes(12, 0, now, zone))
    }

    @Test fun passedTimeAndExactTimeUseTomorrow() {
        assertEquals(at("2026-10-13T12:00:00+02:00"), nextSnoozeClockTime(12, 0, at("2026-10-12T18:00:00+02:00"), zone))
        assertEquals(1440, snoozeClockMinutes(12, 0, at("2026-10-12T12:00:00+02:00"), zone))
    }

    @Test fun midnightAndSecondsNeverResumeEarly() {
        assertEquals(1, snoozeClockMinutes(0, 0, at("2026-10-12T23:59:30+02:00"), zone))
        assertEquals(1, snoozeClockMinutes(12, 0, at("2026-10-12T11:59:59+02:00"), zone))
    }

    @Test fun daylightSavingUsesLocalClockInsteadOfAdding24Hours() {
        val now = at("2026-10-24T18:00:00+02:00")
        assertEquals(at("2026-10-25T12:00:00+01:00"), nextSnoozeClockTime(12, 0, now, zone))
        assertEquals(19 * 60, snoozeClockMinutes(12, 0, now, zone))
        val spring = at("2026-03-28T18:00:00+01:00")
        assertEquals(at("2026-03-29T03:30:00+02:00"), nextSnoozeClockTime(2, 30, spring, zone))
    }

    @Test(expected = IllegalArgumentException::class) fun invalidClockTimeIsRejected() {
        SnoozeClockShortcut("id", "Lunch", 24, 0)
    }
}
