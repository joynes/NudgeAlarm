package se.joynes.nudgealarm.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import se.joynes.nudgealarm.service.notificationAllowed

class ReminderPlaceAssignmentTest {
    private lateinit var db: NagDatabase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NagDatabase::class.java).build() }
    @After fun close() { db.close() }

    @Test fun assignmentPreservesQuestAndImmediatelyChangesNotificationEligibility() = runTest {
        val rule = ReminderEntity("laundry", "Laundry", "0 9 * * *", nagIntervalMinutes = 30, sticky = true)
        db.reminderDao().insert(rule)
        val nags = NagRepository(db.nagStateDao())
        val (_, key) = nags.tryFire(rule.toReminderConfig(), System.currentTimeMillis())
        val occurrenceBefore = nags.getByKey(key)
        val home = SavedPlaceRepository(db.savedPlaceDao()).createPlace("Home", 59.0, 18.0, 250)
        db.assignReminderPlace(rule.id, home.id)
        val updated = db.reminderDao().getById(rule.id)!!
        assertEquals(rule.copy(placeId = home.id, updatedAt = updated.updatedAt), updated)
        val rules = mapOf(rule.id to updated.toReminderConfig())
        assertFalse(notificationAllowed(rule.id, rules) { it.placeId == null })
        assertTrue(notificationAllowed(rule.id, rules) { it.placeId == home.id })
        assertEquals(occurrenceBefore, nags.getByKey(key))
    }

    @Test fun staleSelectionsCannotRecreateDeletedQuestOrAttachDeletedPlace() = runTest {
        val home = SavedPlaceRepository(db.savedPlaceDao()).createPlace("Home", 59.0, 18.0, 250)
        assertTrue(runCatching { db.assignReminderPlace("deleted", home.id) }.isFailure)
        assertNull(db.reminderDao().getById("deleted"))
        db.reminderDao().insert(ReminderEntity("laundry", "Laundry", "0 9 * * *"))
        assertTrue(runCatching { db.assignReminderPlace("laundry", "deleted") }.isFailure)
        assertNull(db.reminderDao().getById("laundry")!!.placeId)
    }

    @Test fun placeWithoutActivePositionIsRejected() = runTest {
        db.reminderDao().insert(ReminderEntity("laundry", "Laundry", "0 9 * * *"))
        db.savedPlaceDao().savePlace(SavedPlaceEntity("home", "Home"))
        assertTrue(runCatching { db.assignReminderPlace("laundry", "home") }.isFailure)
        assertNull(db.reminderDao().getById("laundry")!!.placeId)
    }
}
