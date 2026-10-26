package se.joynes.nudgealarm.notification

import android.content.Context

/** Device-local activity state, deliberately excluded from portable settings imports. */
class ReminderAttentionStore(context: Context) {
    private val prefs = context.getSharedPreferences("reminder_attention", Context.MODE_PRIVATE)
    val lastInteractionAt: Long get() = prefs.getLong("last_interaction_at", 0L)

    fun recordInteraction(now: Long = System.currentTimeMillis(), force: Boolean = false) {
        val previous = lastInteractionAt
        // Avoid a disk write for every touch/key event during continuous interaction.
        if (force || now < previous || now - previous >= 1000L) {
            prefs.edit().putLong("last_interaction_at", now).apply()
        }
    }
}

internal fun repeatAlertAcknowledged(
    triggeredAt: Long,
    snoozedUntil: Long?,
    lastInteractionAt: Long,
    graceMinutes: Int,
    now: Long
): Boolean = graceMinutes > 0 && lastInteractionAt > 0L && now >= lastInteractionAt &&
    now - lastInteractionAt < graceMinutes * 60_000L &&
    triggeredAt <= lastInteractionAt && (snoozedUntil ?: 0L) <= lastInteractionAt
