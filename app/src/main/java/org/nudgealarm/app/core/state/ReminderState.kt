package org.nudgealarm.app.core.state

data class ReminderState(
    val ruleId: String,
    val occurrenceTime: Long,
    val isDone: Boolean = false,
    val snoozedUntil: Long? = null,
    val nagCount: Int = 0
) {
    val isSnoozed: Boolean
        get() = snoozedUntil != null && System.currentTimeMillis() < snoozedUntil
}
