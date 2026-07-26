package se.joynes.nudgealarm.core.event

import org.junit.Assert.*
import org.junit.Test

class EventTest {

    // === Service Lifecycle Events ===

    @Test
    fun serviceStartedMessage() {
        val event = Event.ServiceStarted()
        assertEquals("SERVICE_STARTED", event.message)
    }

    @Test
    fun serviceStoppedMessage() {
        val event = Event.ServiceStopped(reason = "user_requested")
        assertEquals("SERVICE_STOPPED: user_requested", event.message)
    }

    @Test
    fun serviceStoppedDefaultReason() {
        val event = Event.ServiceStopped()
        assertEquals("SERVICE_STOPPED: user", event.message)
    }

    @Test
    fun serviceKilledMessage() {
        val event = Event.ServiceKilled()
        assertEquals("SERVICE_KILLED", event.message)
    }

    // === Config Events ===

    @Test
    fun configLoadedMessage() {
        val event = Event.ConfigLoaded(ruleCount = 5)
        assertEquals("CONFIG_LOADED: 5 rules", event.message)
    }

    @Test
    fun configReloadedMessage() {
        val event = Event.ConfigReloaded(ruleCount = 3)
        assertEquals("CONFIG_RELOADED: 3 rules", event.message)
    }

    @Test
    fun configErrorMessage() {
        val event = Event.ConfigError(error = "File not found")
        assertEquals("CONFIG_ERROR: File not found", event.message)
    }

    // === Scheduling Events ===

    @Test
    fun waitingUntilMessage() {
        val event = Event.WaitingUntil(
            targetTime = System.currentTimeMillis() + 60000,
            delayMs = 60000
        )
        assertEquals("WAITING: 60s until trigger", event.message)
    }

    // === Trigger Events ===

    @Test
    fun reminderTriggeredMessage() {
        val event = Event.ReminderTriggered(ruleId = "morning_routine")
        assertEquals("REMINDER_TRIGGERED: morning_routine", event.message)
    }

    @Test
    fun nagFiredMessage() {
        val event = Event.NagFired(ruleId = "test", nagCount = 5, maxNags = 100)
        assertEquals("NAG_FIRED: test (5/100)", event.message)
    }

    // === User Action Events ===

    @Test
    fun userTappedDoneMessage() {
        val event = Event.UserTappedDone(ruleId = "morning_vitamins")
        assertEquals("USER_TAPPED_DONE: morning_vitamins", event.message)
    }

    @Test
    fun userTappedSnoozeMessage() {
        val event = Event.UserTappedSnooze(ruleId = "workout", duration = "30 minutes")
        assertEquals("USER_TAPPED_SNOOZE: workout for 30 minutes", event.message)
    }

    @Test
    fun userTappedCancelMessage() {
        val event = Event.UserTappedCancel(ruleId = "skip_this")
        assertEquals("USER_TAPPED_CANCEL: skip_this", event.message)
    }

    @Test
    fun snoozeEndedMessage() {
        val event = Event.SnoozeEnded(ruleId = "test_rule")
        assertEquals("SNOOZE_ENDED: test_rule", event.message)
    }

    // === System Events ===

    @Test
    fun deviceBootedMessage() {
        val event = Event.DeviceBooted()
        assertEquals("DEVICE_BOOTED", event.message)
    }

    @Test
    fun batteryOptimizationEnabledMessage() {
        val event = Event.BatteryOptimizationChanged(isOptimized = true)
        assertEquals("BATTERY_OPTIMIZATION: enabled", event.message)
    }

    @Test
    fun batteryOptimizationDisabledMessage() {
        val event = Event.BatteryOptimizationChanged(isOptimized = false)
        assertEquals("BATTERY_OPTIMIZATION: disabled", event.message)
    }

    @Test
    fun appForegroundedMessage() {
        val event = Event.AppForegrounded()
        assertEquals("APP_FOREGROUNDED", event.message)
    }

    @Test
    fun appBackgroundedMessage() {
        val event = Event.AppBackgrounded()
        assertEquals("APP_BACKGROUNDED", event.message)
    }

    // === UI Events ===

    @Test
    fun uiActionMessage() {
        val event = Event.UiAction(action = "button_clicked")
        assertEquals("UI: button_clicked", event.message)
    }

    @Test
    fun navigatedToMessage() {
        val event = Event.NavigatedTo(screen = "Settings")
        assertEquals("NAV: Settings", event.message)
    }

    @Test
    fun configFileSelectedMessage() {
        val event = Event.ConfigFileSelected(path = "/data/config.yaml")
        assertEquals("CONFIG_SELECTED: /data/config.yaml", event.message)
    }

    @Test
    fun testConfigCreatedMessage() {
        val event = Event.TestConfigCreated(configName = "test_1min")
        assertEquals("TEST_CONFIG_CREATED: test_1min", event.message)
    }

    @Test
    fun schedulerLoopIterationMessage() {
        val event = Event.SchedulerLoopIteration(detail = "checking next trigger")
        assertEquals("SCHEDULER: checking next trigger", event.message)
    }

    @Test
    fun debugMessage() {
        val event = Event.Debug(detail = "some debug info")
        assertEquals("DEBUG: some debug info", event.message)
    }

    // === Timestamp Tests ===

    @Test
    fun eventHasTimestamp() {
        val before = System.currentTimeMillis()
        val event = Event.ServiceStarted()
        val after = System.currentTimeMillis()

        assertTrue(event.timestamp >= before)
        assertTrue(event.timestamp <= after)
    }

    @Test
    fun eventWithCustomTimestamp() {
        val customTime = 1000000L
        val event = Event.ServiceStarted(timestamp = customTime)
        assertEquals(customTime, event.timestamp)
    }

    @Test
    fun formattedTimeIsNotEmpty() {
        val event = Event.ServiceStarted()
        assertTrue(event.formattedTime.isNotEmpty())
        // Format should be HH:mm:ss
        assertTrue(event.formattedTime.matches(Regex("\\d{2}:\\d{2}:\\d{2}")))
    }

    @Test
    fun formattedDateIsNotEmpty() {
        val event = Event.ServiceStarted()
        assertTrue(event.formattedDate.isNotEmpty())
        // Format should be yyyy-MM-dd HH:mm:ss
        assertTrue(event.formattedDate.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
    }

    // === Equality Tests ===

    @Test
    fun eventsWithSameDataAreEqual() {
        val timestamp = 1000L
        val event1 = Event.UserTappedDone(timestamp = timestamp, ruleId = "test")
        val event2 = Event.UserTappedDone(timestamp = timestamp, ruleId = "test")
        assertEquals(event1, event2)
    }

    @Test
    fun eventsWithDifferentDataAreNotEqual() {
        val timestamp = 1000L
        val event1 = Event.UserTappedDone(timestamp = timestamp, ruleId = "test1")
        val event2 = Event.UserTappedDone(timestamp = timestamp, ruleId = "test2")
        assertNotEquals(event1, event2)
    }
}
