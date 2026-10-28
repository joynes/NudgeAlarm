package se.joynes.nudgealarm.notification

import org.junit.Assert.*
import org.junit.Test

class ReminderAttentionTest {
    private val seen = 1_000_000L

    @Test fun existingRemindersWaitUntilTheGraceEnds() {
        assertTrue(repeatAlertAcknowledged(seen - 1, null, seen, 15, seen))
        assertTrue(repeatAlertAcknowledged(seen - 1, null, seen, 15, seen + 899_999))
        assertFalse(repeatAlertAcknowledged(seen - 1, null, seen, 15, seen + 900_000))
    }

    @Test fun laterInteractionRenewsTheBreakAndConfiguredLengthApplies() {
        assertFalse(repeatAlertAcknowledged(seen - 1, null, seen, 5, seen + 600_000))
        assertTrue(repeatAlertAcknowledged(seen - 1, null, seen + 500_000, 5, seen + 600_000))
        assertTrue(repeatAlertAcknowledged(seen - 1, null, seen, 30, seen + 1_000_000))
    }

    @Test fun newReminderOrLaterSnoozeExpiryStillAlertsEvenInAMixedNotification() {
        val old = repeatAlertAcknowledged(seen - 1, null, seen, 15, seen + 60_000)
        val fresh = repeatAlertAcknowledged(seen + 1, null, seen, 15, seen + 60_000)
        val snoozed = repeatAlertAcknowledged(seen - 1, seen + 30_000, seen, 15, seen + 60_000)
        assertTrue(old)
        assertFalse(fresh)
        assertFalse(snoozed)
        assertFalse(listOf(old, fresh).all { it })
        assertTrue(repeatAlertAcknowledged(seen - 1, seen - 1, seen, 15, seen + 60_000))
    }

    @Test fun offMissingInteractionAndClockGoingBackDoNotMuteAlerts() {
        assertFalse(repeatAlertAcknowledged(seen - 1, null, seen, 0, seen))
        assertFalse(repeatAlertAcknowledged(1, null, 0, 15, seen))
        assertFalse(repeatAlertAcknowledged(seen - 1, null, seen, 15, seen - 1))
    }

    @Test fun activityBeforeDeliveryAndAScreenWakeCannotAcknowledgeANewReminder() {
        assertFalse(repeatAlertAcknowledged(null, null, seen, 15, seen + 60_000))
        assertFalse(repeatAlertAcknowledged(seen, null, seen, 15, seen + 60_000))
        assertFalse(repeatAlertAcknowledged(seen + 1, null, seen, 15, seen + 60_000))
        assertTrue(repeatAlertAcknowledged(seen, null, seen + 1, 15, seen + 60_000))
        assertFalse(PhoneUseMonitor.isPhoneUseEvent(android.app.usage.UsageEvents.Event.SCREEN_INTERACTIVE))
        assertFalse(PhoneUseMonitor.isPhoneUseEvent(android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED))
        assertTrue(PhoneUseMonitor.isPhoneUseEvent(android.app.usage.UsageEvents.Event.USER_INTERACTION))
        assertTrue(PhoneUseMonitor.isPhoneUseEvent(android.app.usage.UsageEvents.Event.KEYGUARD_HIDDEN))
    }
}
