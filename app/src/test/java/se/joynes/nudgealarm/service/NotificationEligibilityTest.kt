package se.joynes.nudgealarm.service

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import se.joynes.nudgealarm.core.config.ReminderConfig

class NotificationEligibilityTest {
    @Test fun activeNagUsesNewlyAddedPlaceCondition() = runTest {
        val updated = ReminderConfig("laundry", "Laundry", "0 9 * * *", placeId = "home")
        var checkedPlace: String? = null
        assertFalse(notificationAllowed("laundry", mapOf("laundry" to updated)) {
            checkedPlace = it.placeId
            false // Away from home
        })
        assertEquals("home", checkedPlace)
        assertTrue(notificationAllowed("laundry", mapOf("laundry" to updated)) { true })
    }

    @Test fun removedOrDisabledRulesNeverNotify() = runTest {
        assertFalse(notificationAllowed("laundry", emptyMap()) { error("Should not check location") })
        val disabled = ReminderConfig("laundry", "Laundry", "0 9 * * *", enabled = false)
        assertFalse(notificationAllowed("laundry", mapOf("laundry" to disabled)) { error("Should not check location") })
    }

}
