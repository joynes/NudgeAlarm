package se.joynes.nudgealarm.ui

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import se.joynes.nudgealarm.core.config.ReminderConfig
import se.joynes.nudgealarm.notification.ReminderNotification as Notifications

class NotificationContentRegressionTest {
    @Test fun serviceNotificationDescribesNextQuestActiveCountOrMonitoring() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Next: Laundry at 09:00", Notifications.buildServiceNotification(context, "Laundry", "09:00").extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("3 active reminders", Notifications.buildServiceNotification(context, activeRulesCount = 3).extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("Monitoring reminders", Notifications.buildServiceNotification(context).extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test fun singleAndGroupedRemindersKeepTheirTitlesActionsAndCounts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rule = ReminderConfig("laundry", "Laundry", "0 9 * * *", maxNags = 8)
        val first = Notifications.buildReminderNotification(context, rule)
        assertEquals("Laundry", first.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(listOf("DONE", "15m", "PLACE"), first.actions.map { it.title.toString() })
        val repeated = Notifications.buildReminderNotificationFromNag(context, rule.id, rule.title, 2, 8)
        assertEquals("Laundry (2/8)", repeated.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(listOf("DONE", "15m", "1h"), repeated.actions.map { it.title.toString() })
        val summary = Notifications.buildGroupSummaryNotification(context, listOf("Laundry", "Dishes"))
        assertEquals("2 active quests", summary.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(summary.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        assertEquals(listOf("Laundry", "Dishes"), summary.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)!!.map { it.toString() })
    }
}
