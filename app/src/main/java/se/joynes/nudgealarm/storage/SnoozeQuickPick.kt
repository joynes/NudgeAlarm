package se.joynes.nudgealarm.storage

import java.time.ZoneId

internal sealed interface SnoozeQuickPick {
    val resumeAt: Long
    data class Duration(val minutes: Int, override val resumeAt: Long) : SnoozeQuickPick
    data class Clock(val shortcut: SnoozeClockShortcut, override val resumeAt: Long) : SnoozeQuickPick
}

/** Saved clock times supplement (never displace) the configured duration choices. */
internal fun snoozeQuickPicks(durations: List<Int>, clocks: List<SnoozeClockShortcut>, now: Long,
    zone: ZoneId = ZoneId.systemDefault()): List<SnoozeQuickPick> =
    (durations.map { SnoozeQuickPick.Duration(it, now + it * 60_000L) } +
        clocks.map { SnoozeQuickPick.Clock(it, nextSnoozeClockTime(it.hour, it.minute, now, zone)) })
        .sortedBy { it.resumeAt }
