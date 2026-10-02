package se.joynes.nudgealarm.service

import se.joynes.nudgealarm.core.config.ReminderConfig

// Resolve the current saved rule, not the configuration captured when a nag started.
internal suspend fun notificationAllowed(
    ruleId: String,
    currentRules: Map<String, ReminderConfig>,
    locationAllows: suspend (ReminderConfig) -> Boolean
): Boolean {
    val rule = currentRules[ruleId] ?: return false
    return rule.enabled && locationAllows(rule)
}

internal fun positionIsFresh(timestamp: Long, now: Long): Boolean =
    now - timestamp in 0 until 120_000L
