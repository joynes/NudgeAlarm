package se.joynes.nudgealarm.storage

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

internal data class SnoozeClockShortcut(val id: String, val name: String, val hour: Int, val minute: Int) {
    init {
        require(id.isNotBlank() && name.isNotBlank())
        require(hour in 0..23 && minute in 0..59)
    }
}

/** Next occurrence in local time, including midnight and daylight-saving changes. */
internal fun nextSnoozeClockTime(hour: Int, minute: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val current = Instant.ofEpochMilli(now).atZone(zone)
    val time = LocalTime.of(hour, minute)
    var target = current.toLocalDate().atTime(time).atZone(zone)
    if (!target.toInstant().isAfter(current.toInstant())) {
        target = current.toLocalDate().plusDays(1).atTime(time).atZone(zone)
    }
    return target.toInstant().toEpochMilli()
}

/** Existing snooze/quiet-mode scheduling uses whole minutes; never resume before the chosen time. */
internal fun snoozeClockMinutes(hour: Int, minute: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
    ((nextSnoozeClockTime(hour, minute, now, zone) - now + 59_999L) / 60_000L).toInt()
