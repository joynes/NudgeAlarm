package se.joynes.nudgealarm.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedPlaceManagementTest {
    private lateinit var database: NagDatabase
    private lateinit var places: SavedPlaceRepository
    private lateinit var reminders: ReminderRepository

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), NagDatabase::class.java
        ).allowMainThreadQueries().build()
        places = SavedPlaceRepository(database.savedPlaceDao())
        reminders = ReminderRepository(database.reminderDao())
    }

    @After
    fun teardown() = database.close()

    @Test
    fun deletingPlaceUnlinksQuestsAndRemovesAllPositions() = runTest {
        val home = places.createPlace("Home", 59.3, 18.0, 250)
        places.addLocation(home.id, 36.7, -4.4, 400, "Spain")
        val linked = reminders.create("Dishes", "0 9 * * *", placeId = home.id)
        val unrelated = reminders.create("Walk", "0 10 * * *")

        assertEquals(1, places.linkedReminderCount(home.id))
        places.deletePlace(home.id)

        assertTrue(places.getAll().isEmpty())
        assertNull(database.savedPlaceDao().getLocations(home.id).firstOrNull())
        assertNull(reminders.getById(linked.id)?.placeId)
        assertNull(reminders.getById(unrelated.id)?.placeId)
    }

    @Test
    fun deletingActivePositionSelectsAnotherWithoutChangingQuestLink() = runTest {
        val home = places.createPlace("Home", 59.3, 18.0, 250)
        val abroad = places.addLocation(home.id, 36.7, -4.4, 400, "Spain")
        val quest = reminders.create("Dishes", "0 9 * * *", placeId = home.id)

        places.deletePosition(home.id, abroad.id)

        assertEquals(home.id, reminders.getById(quest.id)?.placeId)
        val remaining = places.getAll().single()
        assertEquals(1, remaining.locations.size)
        assertEquals(remaining.locations.single().id, remaining.place.activeLocationId)
    }

    @Test
    fun editingPlaceAndPositionPreservesIdsAndQuestLink() = runTest {
        val home = places.createPlace("Home", 59.3, 18.0, 250)
        val position = places.getAll().single().activeLocation!!
        val quest = reminders.create("Dishes", "0 9 * * *", placeId = home.id)

        places.renamePlace(home.id, "New home")
        places.updatePosition(home.id, position.id, "Stockholm", 59.4, 18.1, 500)

        val updated = places.getAll().single()
        assertEquals(home.id, updated.place.id)
        assertEquals("New home", updated.place.name)
        assertEquals(position.id, updated.activeLocation?.id)
        assertEquals("Stockholm", updated.activeLocation?.label)
        assertEquals(500, updated.activeLocation?.radiusMeters)
        assertEquals(home.id, reminders.getById(quest.id)?.placeId)
    }
}
