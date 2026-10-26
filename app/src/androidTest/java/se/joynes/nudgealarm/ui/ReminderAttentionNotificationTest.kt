package se.joynes.nudgealarm.ui

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import se.joynes.nudgealarm.database.NagStateEntity
import se.joynes.nudgealarm.notification.ReminderNotification

class ReminderAttentionNotificationTest {
    @Test fun graceNotificationsAreSilentEvenIfAndroidHasNoPreviousNotification() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val nag = NagStateEntity("laundry@1", "laundry", 1, "Laundry", 1, 1, 100, 300000,
            "ACTIVE", null, null)
        for (nags in listOf(listOf(nag), listOf(nag, nag.copy(ruleId = "dishes")))) {
            val quiet = ReminderNotification.buildCombinedNotification(context, nags, onlyAlertOnce = true)
            // Only-alert-once alone can still sound on the first post. Silent group routing
            // also suppresses channel sound/vibration when no previous notification exists.
            assertEquals(Notification.GROUP_ALERT_SUMMARY, quiet.groupAlertBehavior)
            assertNotNull(quiet.group)
            assertFalse(quiet.flags and Notification.FLAG_GROUP_SUMMARY != 0)
            val loud = ReminderNotification.buildCombinedNotification(context, nags)
            assertEquals(Notification.GROUP_ALERT_ALL, loud.groupAlertBehavior)
        }
    }
}
