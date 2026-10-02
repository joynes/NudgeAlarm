package se.joynes.nudgealarm.location

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GeofenceStateStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = GeofenceStateStore(context)
    private val home = GeofenceSpec("home", "stockholm", 59.3, 18.0, 250)

    @Before fun setup() { store.invalidate("Test") }
    @After fun cleanup() { store.invalidate("Waiting for place monitoring to start") }

    @Test fun unknownBlocksAndAllLinkedQuestsReusePersistedEntryUntilExit() {
        store.replace(listOf(home), "first")
        assertFalse(store.allows(home))
        store.transition(listOf("first:0"), true)
        assertFalse(store.allows(home)) // Registration has not succeeded yet.
        store.ready("first")
        repeat(100) { assertTrue(GeofenceStateStore(context).allows(home)) }
        store.transition(listOf("first:0"), false)
        assertFalse(store.allows(home))
    }

    @Test fun changedPositionAndRadiusRejectOldCacheAndLateCallbacks() {
        store.replace(listOf(home), "first")
        store.ready("first")
        store.transition(listOf("first:0"), true)
        assertFalse(store.allows(home.copy(radiusMeters = 200)))
        val abroad = home.copy(positionId = "holiday", latitude = 40.4)
        store.replace(listOf(abroad), "second")
        store.ready("second")
        assertFalse(store.transition(listOf("first:0"), true))
        assertFalse(store.allows(abroad))
        store.transition(listOf("second:0"), true)
        assertTrue(store.allows(abroad))
        assertFalse(store.allows(home))
    }

    @Test fun rebootErrorsOrRevokedPermissionInvalidatePresence() {
        store.replace(listOf(home), "first")
        store.ready("first")
        store.transition(listOf("first:0"), true)
        store.invalidate("Location unavailable")
        store.ready("first")
        assertFalse(store.transition(listOf("first:0"), true))
        assertFalse(store.allows(home))
        assertEquals("Location unavailable", store.status())
    }

    @Test fun eachPlaceHasIndependentPresence() {
        val office = home.copy(placeId = "office", positionId = "work")
        store.replace(listOf(home, office), "first")
        store.ready("first")
        store.transition(listOf("first:0"), true)
        assertTrue(store.allows(home))
        assertFalse(store.allows(office))
    }
}
