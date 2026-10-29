package se.joynes.nudgealarm.database

/** One visible state per quest. An unexpired quest snooze also covers overlapping occurrences. */
internal fun currentNagStates(nags: List<NagStateEntity>, now: Long): List<NagStateEntity> {
    val newest = compareBy<NagStateEntity> { it.scheduledTime }.thenBy { it.triggeredAt }.thenBy { it.occurrenceKey }
    return nags.filter { it.status == NagStatus.ACTIVE.name || it.status == NagStatus.SNOOZED.name }
        .groupBy { it.ruleId }.values.map { occurrences ->
            occurrences.filter { it.status == NagStatus.SNOOZED.name && (it.snoozedUntil ?: 0) > now }
                .maxWithOrNull(newest) ?: occurrences.maxWith(newest)
        }.sortedWith(newest.reversed())
}
