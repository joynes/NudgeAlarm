package se.joynes.nudgealarm.database

import androidx.room.withTransaction
import se.joynes.nudgealarm.core.cron.CronExpression
import java.util.Calendar

/** Bulk actions have the same durable statistics as individual actions. */
internal suspend fun NagDatabase.finishPendingQuests(ruleIds: List<String>, outcome: NagStatus) = withTransaction {
    require(outcome == NagStatus.COMPLETED || outcome == NagStatus.CANCELLED)
    val repository = NagRepository(nagStateDao())
    val analytics = AnalyticsRepository(nagHistoryDao())
    for (ruleId in ruleIds.distinct()) {
        var nag = if (outcome == NagStatus.COMPLETED) repository.markDoneByRuleId(ruleId)
            else repository.markCancelledByRuleId(ruleId)
        // Today's Schedule also contains quests that have not fired yet. Persist their
        // scheduled occurrence, so the scheduler cannot start nagging after a bulk action.
        if (nag == null) {
            val rule = reminderDao().getById(ruleId)?.toReminderConfig() ?: continue
            val dayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val scheduled = runCatching {
                if (rule.schedule.startsWith("once:")) rule.schedule.removePrefix("once:").toLong()
                else CronExpression.parse(rule.schedule).nextTriggerTime(dayStart)
            }.getOrNull() ?: continue
            val (created, key) = repository.tryFire(rule, scheduled / 60_000 * 60_000)
            if (created) {
                if (outcome == NagStatus.COMPLETED) repository.markDone(key) else repository.markCancelled(key)
                nag = repository.getByKey(key)
            }
        }
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
