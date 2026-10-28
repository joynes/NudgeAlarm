package se.joynes.nudgealarm.notification

import android.content.Context
import se.joynes.nudgealarm.database.NagStateEntity

/** Device-local activity state, deliberately excluded from portable settings imports. */
class ReminderAttentionStore(context: Context) {
    private val prefs = context.getSharedPreferences("reminder_attention", Context.MODE_PRIVATE)
    val lastInteractionAt: Long get() = prefs.getLong("last_interaction_at", 0L)

    fun firstPresentedAt(nag: NagStateEntity): Long? = prefs.getLong(presentationKey(nag), 0L).takeIf { it > 0 }

    /** Each occurrence/snooze must be presented before subsequent phone use can acknowledge it. */
    fun recordPresentations(shown: List<NagStateEntity>, active: List<NagStateEntity>, now: Long) {
        val retained = active.map(::presentationKey).toSet()
        val editor = prefs.edit()
        var changed = false
        prefs.all.keys.filter { it.startsWith("presented:") && it !in retained }.forEach {
            editor.remove(it); changed = true
        }
        shown.forEach { nag ->
            val previous = firstPresentedAt(nag)
            if (previous == null || previous > now) {
                editor.putLong(presentationKey(nag), now); changed = true
            }
        }
        if (changed) editor.apply()
    }

    fun presentationSnapshot(): Map<String, Long> = prefs.all.entries
        .filter { it.key.startsWith("presented:") }.associate { it.key.removePrefix("presented:") to (it.value as Long) }

    fun recordInteraction(now: Long = System.currentTimeMillis(), force: Boolean = false) {
        val previous = lastInteractionAt
        // Avoid a disk write for every touch/key event during continuous interaction.
        if (force || now < previous || now - previous >= 1000L) {
            prefs.edit().putLong("last_interaction_at", now).apply()
        }
    }
    private fun presentationKey(nag: NagStateEntity) = "presented:${nag.occurrenceKey}#${nag.snoozeCount}"
}

internal fun repeatAlertAcknowledged(
    firstPresentedAt: Long?,
    snoozedUntil: Long?,
    lastInteractionAt: Long,
    graceMinutes: Int,
    now: Long
): Boolean = graceMinutes > 0 && lastInteractionAt > 0L && now >= lastInteractionAt &&
    now - lastInteractionAt < graceMinutes * 60_000L &&
    firstPresentedAt != null && firstPresentedAt < lastInteractionAt && (snoozedUntil ?: 0L) <= lastInteractionAt
