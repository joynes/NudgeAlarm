package se.joynes.nudgealarm.core.state

import kotlin.time.Duration

class StateManager {
    private val states = mutableMapOf<String, ReminderState>()

    fun getState(ruleId: String): ReminderState? = states[ruleId]

    fun initState(ruleId: String, occurrenceTime: Long) {
        states[ruleId] = ReminderState(
            ruleId = ruleId,
            occurrenceTime = occurrenceTime
        )
    }

    fun markDone(ruleId: String) {
        states[ruleId]?.let { state ->
            states[ruleId] = state.copy(isDone = true)
        }
    }

    fun snooze(ruleId: String, duration: Duration) {
        states[ruleId]?.let { state ->
            states[ruleId] = state.copy(
                snoozedUntil = System.currentTimeMillis() + duration.inWholeMilliseconds
            )
        }
    }

    fun clearSnooze(ruleId: String) {
        states[ruleId]?.let { state ->
            states[ruleId] = state.copy(snoozedUntil = null)
        }
    }

    fun incrementNag(ruleId: String): Int {
        val state = states[ruleId] ?: return 0
        val newCount = state.nagCount + 1
        states[ruleId] = state.copy(nagCount = newCount)
        return newCount
    }

    fun isDone(ruleId: String): Boolean = states[ruleId]?.isDone == true

    fun isSnoozed(ruleId: String): Boolean = states[ruleId]?.isSnoozed == true

    fun getSnoozedUntil(ruleId: String): Long? = states[ruleId]?.snoozedUntil

    fun clear(ruleId: String) {
        states.remove(ruleId)
    }

    fun clearAll() {
        states.clear()
    }
}
