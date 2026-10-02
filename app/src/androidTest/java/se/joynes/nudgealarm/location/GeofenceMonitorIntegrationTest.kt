package se.joynes.nudgealarm.location

import android.Manifest
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import se.joynes.nudgealarm.core.config.ReminderConfig
import se.joynes.nudgealarm.database.NagDatabase
import se.joynes.nudgealarm.database.SavedPlaceRepository

class GeofenceMonitorIntegrationTest {
    @Test fun realAndroidRegistrationIsSharedAndUnchangedSyncReusesCache() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NagDatabase::class.java).build()
        val places = SavedPlaceRepository(database.savedPlaceDao())
        val monitor = GeofenceMonitor(context, places)
        val state = GeofenceStateStore(context)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val granted = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        granted.forEach { automation.grantRuntimePermission(context.packageName, it) }
        try {
            assertNull("Emulator must have Google Play services and Location on", monitor.prerequisites())
            val home = places.createPlace("Test home", 59.3, 18.0, 250)
            val laundry = ReminderConfig("laundry", "Laundry", "0 9 * * *", placeId = home.id)
            val dishes = laundry.copy(id = "dishes", title = "Dishes")
            monitor.sync(listOf(laundry, dishes))
            assertEquals("Battery-saving place monitoring is on", state.status())
            assertEquals(1, state.requests().size)
            val requests = state.requests()
            state.transition(requests.values.toList(), true)
            assertTrue(monitor.allows(laundry))
            assertTrue(monitor.allows(dishes))
            repeat(3) { monitor.sync(listOf(laundry, dishes)) }
            assertEquals(requests, state.requests())
            assertTrue(monitor.allows(laundry))
            state.transition(requests.values.toList(), false)
            assertFalse(monitor.allows(laundry))
            assertFalse(monitor.allows(dishes))
        } finally {
            monitor.stop()
            database.close()
            // Runtime permission revocation kills the instrumented process; leave debug grants intact.
        }
    }
}
