package org.nudgealarm.app.core.event

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class Event {
    abstract val timestamp: Long

    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    val formattedDate: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    abstract val message: String

    // Service lifecycle
    data class ServiceStarted(
        override val timestamp: Long = System.currentTimeMillis()
    ) : Event() {
        override val message: String = "SERVICE_STARTED"
    }

    data class ServiceStopped(
        override val timestamp: Long = System.currentTimeMillis(),
        val reason: String = "user"
    ) : Event() {
        override val message: String = "SERVICE_STOPPED: $reason"
    }

    data class ServiceKilled(
        override val timestamp: Long = System.currentTimeMillis()
    ) : Event() {
        override val message: String = "SERVICE_KILLED"
    }

    // Config
    data class ConfigLoaded(
        override val timestamp: Long = System.currentTimeMillis(),
        val ruleCount: Int
    ) : Event() {
        override val message: String = "CONFIG_LOADED: $ruleCount rules"
    }

    data class ConfigReloaded(
        override val timestamp: Long = System.currentTimeMillis(),
        val ruleCount: Int
    ) : Event() {
        override val message: String = "CONFIG_RELOADED: $ruleCount rules"
    }

    data class ConfigError(
        override val timestamp: Long = System.currentTimeMillis(),
        val error: String
    ) : Event() {
        override val message: String = "CONFIG_ERROR: $error"
    }

    // Scheduling
    data class NextEventComputed(
        override val timestamp: Long = System.currentTimeMillis(),
        val ruleId: String,
        val triggerTime: Long
    ) : Event() {
        private val triggerFormatted: String
            get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(triggerTime))

        override val message: String = "NEXT_EVENT: $ruleId at $triggerFormatted"
    }

    data class WaitingUntil(
        override val timestamp: Long = System.currentTimeMillis(),
        val targetTime: Long,
        val delayMs: Long
    ) : Event() {
        override val message: String = "WAITING: ${delayMs / 1000}s until trigger"
    }

    // Triggers
    data class ReminderTriggered(
        override val timestamp: Long = System.currentTimeMillis(),
        val ruleId: String
    ) : Event() {
        override val message: String = "REMINDER_TRIGGERED: $ruleId"
    }

    data class NagFired(
        override val timestamp: Long = System.currentTimeMillis(),
        val ruleId: String,
        val nagCount: Int,
        val maxNags: Int
    ) : Event() {
        override val message: String = "NAG_FIRED: $ruleId ($nagCount/$maxNags)"
    }

    // User actions
    data class UserTappedDone(
        override val timestamp: Long = System.currentTimeMillis(),
        val ruleId: String
    ) : Event() {
        override val message: String = "USER_TAPPED_DONE: $ruleId"
    }

    data class UserTappedSnooze(
        override val timestamp: Long = System.currentTimeMillis(),
        val ruleId: String,
        val duration: String
    ) : Event() {
        override val message: String = "USER_TAPPED_SNOOZE: $ruleId for $duration"
    }

    data class UserTappedCancel(
        override val timestamp: Long = System.currentTimeMillis(),
        val ruleId: String
    ) : Event() {
        override val message: String = "USER_TAPPED_CANCEL: $ruleId"
    }

    data class SnoozeEnded(
        override val timestamp: Long = System.currentTimeMillis(),
        val ruleId: String
    ) : Event() {
        override val message: String = "SNOOZE_ENDED: $ruleId"
    }

    // System events
    data class DeviceBooted(
        override val timestamp: Long = System.currentTimeMillis()
    ) : Event() {
        override val message: String = "DEVICE_BOOTED"
    }

    data class BatteryOptimizationChanged(
        override val timestamp: Long = System.currentTimeMillis(),
        val isOptimized: Boolean
    ) : Event() {
        override val message: String = "BATTERY_OPTIMIZATION: ${if (isOptimized) "enabled" else "disabled"}"
    }

    data class AppForegrounded(
        override val timestamp: Long = System.currentTimeMillis()
    ) : Event() {
        override val message: String = "APP_FOREGROUNDED"
    }

    data class AppBackgrounded(
        override val timestamp: Long = System.currentTimeMillis()
    ) : Event() {
        override val message: String = "APP_BACKGROUNDED"
    }

    // UI actions
    data class UiAction(
        override val timestamp: Long = System.currentTimeMillis(),
        val action: String
    ) : Event() {
        override val message: String = "UI: $action"
    }

    data class NavigatedTo(
        override val timestamp: Long = System.currentTimeMillis(),
        val screen: String
    ) : Event() {
        override val message: String = "NAV: $screen"
    }

    data class ConfigFileSelected(
        override val timestamp: Long = System.currentTimeMillis(),
        val path: String
    ) : Event() {
        override val message: String = "CONFIG_SELECTED: $path"
    }

    data class TestConfigCreated(
        override val timestamp: Long = System.currentTimeMillis(),
        val configName: String
    ) : Event() {
        override val message: String = "TEST_CONFIG_CREATED: $configName"
    }

    data class SchedulerLoopIteration(
        override val timestamp: Long = System.currentTimeMillis(),
        val detail: String
    ) : Event() {
        override val message: String = "SCHEDULER: $detail"
    }

    data class CronParsed(
        override val timestamp: Long = System.currentTimeMillis(),
        val ruleId: String,
        val schedule: String,
        val nextTime: String
    ) : Event() {
        override val message: String = "CRON_PARSED: $ruleId schedule=$schedule next=$nextTime"
    }

    data class Debug(
        override val timestamp: Long = System.currentTimeMillis(),
        val detail: String
    ) : Event() {
        override val message: String = "DEBUG: $detail"
    }

    data class Crash(
        override val timestamp: Long = System.currentTimeMillis(),
        val exceptionClass: String,
        val exceptionMessage: String,
        val stackTrace: String
    ) : Event() {
        override val message: String = "CRASH: $exceptionClass: $exceptionMessage"
    }
}
