package se.joynes.nudgealarm.database

import androidx.room.withTransaction

/** Bulk actions have the same durable statistics as individual actions. */
internal suspend fun NagDatabase.finishPendingQuests(ruleIds: List<String>, outcome: NagStatus) = withTransaction {
    require(outcome == NagStatus.COMPLETED || outcome == NagStatus.CANCELLED)
    val repository = NagRepository(nagStateDao())
    val analytics = AnalyticsRepository(nagHistoryDao())
    for (ruleId in ruleIds.distinct()) {
        val nag = if (outcome == NagStatus.COMPLETED) repository.markDoneByRuleId(ruleId)
            else repository.markCancelledByRuleId(ruleId)
        if (nag != null) analytics.recordCompletion(nag, outcome)
    }
}

/** Status and durable statistics must commit together, including when cleanup runs twice. */
internal suspend fun NagDatabase.expireStaleReminders(cutoff: Long, now: Long): List<String> = withTransaction {
    val sticky = reminderDao().getAll().filter { it.sticky }.map { it.id }.toSet()
    val pending = nagStateDao().getActiveNags()
    // A quest-wide pause also protects older overlapping occurrences.
    val paused = pending.filter { it.status == "SNOOZED" && (it.snoozedUntil ?: 0) > now }
        .map { it.ruleId }.toSet()
    val stale = pending.filter {
        it.status == "ACTIVE" && it.triggeredAt < cutoff && it.ruleId !in sticky && it.ruleId !in paused
    }
    for (nag in stale) {
        nagStateDao().markExpired(nag.occurrenceKey)
        nagHistoryDao().insert(NagHistoryEntity(
            ruleId = nag.ruleId, title = nag.title, scheduledTime = nag.scheduledTime,
            triggeredAt = nag.triggeredAt, completedAt = now, outcome = "EXPIRED",
            nagCount = nag.nagCount, maxNags = nag.maxNags,
            responseTimeMs = (now - nag.triggeredAt).coerceAtLeast(0)
        ))
    }
    stale.map { it.ruleId }.distinct()
}
