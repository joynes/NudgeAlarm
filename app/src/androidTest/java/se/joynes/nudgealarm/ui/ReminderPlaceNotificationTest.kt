package se.joynes.nudgealarm.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import se.joynes.nudgealarm.database.NagStateEntity
import se.joynes.nudgealarm.notification.ReminderNotification

class ReminderPlaceNotificationTest {
    @Test fun unboundNotificationOffersPlaceAndTargetsTheCorrectQuest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val nag = NagStateEntity("laundry@1", "laundry", 1L, "Laundry", 1L, 1, 100, 300000L, "ACTIVE", null, null)
        val notification = ReminderNotification.buildCombinedNotification(context, listOf(nag), unboundRuleIds = setOf(nag.ruleId))
        assertEquals(listOf("DONE", "15m", "PLACE"), notification.actions.map { it.title.toString() })
        assertEquals(ReminderNotification.createPlaceIntent(context, "laundry"), notification.actions.last().actionIntent)
        assertNotEquals(ReminderNotification.createPlaceIntent(context, "dishes"), notification.actions.last().actionIntent)
        val bound = ReminderNotification.buildCombinedNotification(context, listOf(nag))
        assertEquals(listOf("DONE", "15m", "1h"), bound.actions.map { it.title.toString() })
        val combined = ReminderNotification.buildCombinedNotification(context, listOf(nag, nag.copy(ruleId = "dishes")), unboundRuleIds = setOf("laundry", "dishes"))
        assertEquals(listOf("Open app"), combined.actions.map { it.title.toString() })
    }
}
