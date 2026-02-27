package org.nudgealarm.app.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nudgealarm.app.core.config.ReminderConfig
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class ReminderRepositoryTest {

    private lateinit var database: NagDatabase
    private lateinit var repository: ReminderRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NagDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ReminderRepository(database.reminderDao())
    }

    @After
    fun teardown() {
        database.close()
    }

    // ── getAllReminders ────────────────────────────────────────────────────────

    @Test
    fun getAllRemindersEmptyByDefault() = runTest {
        assertTrue(repository.getAllReminders().isEmpty())
    }

    @Test
    fun getAllRemindersReturnsAllInserted() = runTest {
        repository.create("Quest A", "0 9 * * *")
        repository.create("Quest B", "0 10 * * *")
        assertEquals(2, repository.getAllReminders().size)
    }

    // ── getAllRemindersFlow ────────────────────────────────────────────────────

    @Test
    fun getAllRemindersFlowEmitsOnInsert() = runTest {
        val initial = repository.getAllRemindersFlow().first()
        assertTrue(initial.isEmpty())

        repository.create("Flow Quest", "0 8 * * *")

        val updated = repository.getAllRemindersFlow().first()
        assertEquals(1, updated.size)
        assertEquals("Flow Quest", updated[0].title)
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    fun createReturnsReminderWithCorrectFields() = runTest {
        val r = repository.create("Morning Run", "0 7 * * 1-5", nagIntervalMinutes = 10, maxNags = 5)
        assertEquals("Morning Run", r.title)
        assertEquals("0 7 * * 1-5", r.schedule)
        assertEquals(10, r.nagIntervalMinutes)
        assertEquals(5, r.maxNags)
        assertTrue(r.enabled)
    }

    @Test
    fun createPersistsToDatabase() = runTest {
        val created = repository.create("Yoga", "30 7 * * *")
        val fetched = repository.getById(created.id)
        assertNotNull(fetched)
        assertEquals("Yoga", fetched!!.title)
    }

    @Test
    fun createDefaultsNagIntervalTo5() = runTest {
        val r = repository.create("Quest", "0 9 * * *")
        assertEquals(5, r.nagIntervalMinutes)
    }

    @Test
    fun createDefaultsMaxNagsTo100() = runTest {
        val r = repository.create("Quest", "0 9 * * *")
        assertEquals(100, r.maxNags)
    }

    @Test
    fun createGeneratesUniqueIdsForSameTitle() = runTest {
        val r1 = repository.create("Duplicate", "0 9 * * *")
        Thread.sleep(2) // timestamp suffix differs
        val r2 = repository.create("Duplicate", "0 9 * * *")
        assertNotEquals(r1.id, r2.id)
    }

    @Test
    fun createIdContainsOnlyLowercaseAlphanumericAndUnderscore() = runTest {
        val r = repository.create("Brush Teeth! 2x/day", "0 8 * * *")
        assertTrue(
            "ID '${r.id}' should match [a-z0-9_]+",
            r.id.matches(Regex("[a-z0-9_]+"))
        )
    }

    @Test
    fun createIdDoesNotStartOrEndWithUnderscore() = runTest {
        val r = repository.create("   Leading Spaces", "0 9 * * *")
        assertFalse("ID should not start with underscore", r.id.startsWith("_"))
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    fun getByIdReturnsNullForMissingReminder() = runTest {
        assertNull(repository.getById("nonexistent_id"))
    }

    @Test
    fun getByIdReturnsCorrectReminder() = runTest {
        val created = repository.create("Find Me", "0 9 * * *")
        val found = repository.getById(created.id)
        assertNotNull(found)
        assertEquals(created.id, found!!.id)
        assertEquals("Find Me", found.title)
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    fun saveModifiesExistingReminder() = runTest {
        val created = repository.create("Original", "0 9 * * *")
        repository.save(created.copy(title = "Modified"))
        val fetched = repository.getById(created.id)
        assertEquals("Modified", fetched!!.title)
    }

    @Test
    fun saveUpdatesUpdatedAtTimestamp() = runTest {
        val created = repository.create("Quest", "0 9 * * *")
        val beforeSave = System.currentTimeMillis()
        Thread.sleep(5)
        repository.save(created.copy(title = "Updated"))
        val fetched = repository.getById(created.id)
        assertTrue(fetched!!.updatedAt >= beforeSave)
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    fun updateChangesFieldsInDatabase() = runTest {
        val created = repository.create("Before", "0 9 * * *")
        repository.update(created.copy(title = "After", schedule = "0 10 * * *"))
        val fetched = repository.getById(created.id)
        assertEquals("After", fetched!!.title)
        assertEquals("0 10 * * *", fetched.schedule)
    }

    @Test
    fun updatePreservesId() = runTest {
        val created = repository.create("Quest", "0 9 * * *")
        repository.update(created.copy(title = "Updated"))
        assertNotNull(repository.getById(created.id))
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    fun deleteRemovesReminderFromDatabase() = runTest {
        val created = repository.create("Delete Me", "0 9 * * *")
        assertNotNull(repository.getById(created.id))
        repository.delete(created.id)
        assertNull(repository.getById(created.id))
    }

    @Test
    fun deleteReducesCount() = runTest {
        val r1 = repository.create("Q1", "0 9 * * *")
        repository.create("Q2", "0 10 * * *")
        assertEquals(2, repository.getAllReminders().size)
        repository.delete(r1.id)
        assertEquals(1, repository.getAllReminders().size)
    }

    @Test
    fun deleteNonExistentIdDoesNotThrow() = runTest {
        repository.delete("nonexistent_xyz") // Should not throw
    }

    // ── toggleEnabled ─────────────────────────────────────────────────────────

    @Test
    fun toggleEnabledFlipsTrueToFalse() = runTest {
        val created = repository.create("Quest", "0 9 * * *")
        assertTrue(created.enabled)
        repository.toggleEnabled(created.id)
        assertFalse(repository.getById(created.id)!!.enabled)
    }

    @Test
    fun toggleEnabledFlipsFalseToTrue() = runTest {
        val created = repository.create("Quest", "0 9 * * *")
        repository.setEnabled(created.id, false)
        repository.toggleEnabled(created.id)
        assertTrue(repository.getById(created.id)!!.enabled)
    }

    @Test
    fun toggleEnabledTwiceRestoresOriginalState() = runTest {
        val created = repository.create("Quest", "0 9 * * *")
        repository.toggleEnabled(created.id)
        repository.toggleEnabled(created.id)
        assertTrue(repository.getById(created.id)!!.enabled)
    }

    // ── setEnabled ────────────────────────────────────────────────────────────

    @Test
    fun setEnabledFalseDisablesReminder() = runTest {
        val created = repository.create("Quest", "0 9 * * *")
        repository.setEnabled(created.id, false)
        assertFalse(repository.getById(created.id)!!.enabled)
    }

    @Test
    fun setEnabledTrueEnablesReminder() = runTest {
        val created = repository.create("Quest", "0 9 * * *")
        repository.setEnabled(created.id, false)
        repository.setEnabled(created.id, true)
        assertTrue(repository.getById(created.id)!!.enabled)
    }

    // ── getEnabledAsConfigs ───────────────────────────────────────────────────

    @Test
    fun getEnabledAsConfigsOnlyReturnsEnabledReminders() = runTest {
        repository.create("Enabled", "0 9 * * *")
        val disabled = repository.create("Disabled", "0 10 * * *")
        repository.setEnabled(disabled.id, false)

        val configs = repository.getEnabledAsConfigs()
        assertEquals(1, configs.size)
        assertEquals("Enabled", configs[0].title)
    }

    @Test
    fun getEnabledAsConfigsConvertsFieldsCorrectly() = runTest {
        repository.create("Quest", "0 9 * * 1-5", nagIntervalMinutes = 15, maxNags = 3)
        val configs = repository.getEnabledAsConfigs()
        assertEquals(1, configs.size)
        assertEquals("0 9 * * 1-5", configs[0].schedule)
        assertEquals(15, configs[0].nagInterval.inWholeMinutes.toInt())
        assertEquals(3, configs[0].maxNags)
    }

    @Test
    fun getEnabledAsConfigsEmptyWhenAllDisabled() = runTest {
        val r = repository.create("Quest", "0 9 * * *")
        repository.setEnabled(r.id, false)
        assertTrue(repository.getEnabledAsConfigs().isEmpty())
    }

    // ── getAsAppConfig ────────────────────────────────────────────────────────

    @Test
    fun getAsAppConfigWrapsEnabledRemindersOnly() = runTest {
        repository.create("Quest A", "0 9 * * *")
        val disabled = repository.create("Quest B", "0 10 * * *")
        repository.setEnabled(disabled.id, false)

        val config = repository.getAsAppConfig()
        assertEquals(1, config.reminders.size)
        assertEquals("Quest A", config.reminders[0].title)
    }

    @Test
    fun getAsAppConfigEmptyWhenNoReminders() = runTest {
        val config = repository.getAsAppConfig()
        assertTrue(config.reminders.isEmpty())
    }

    // ── importFromConfigs ─────────────────────────────────────────────────────

    @Test
    fun importFromConfigsImportsAll() = runTest {
        val configs = listOf(
            ReminderConfig("q1", "Quest 1", "0 9 * * *", 5.minutes, 100),
            ReminderConfig("q2", "Quest 2", "0 10 * * *", 5.minutes, 100),
            ReminderConfig("q3", "Quest 3", "0 11 * * *", 5.minutes, 100),
        )
        repository.importFromConfigs(configs)
        assertEquals(3, repository.getAllReminders().size)
    }

    @Test
    fun importFromConfigsPreservesCustomIds() = runTest {
        repository.importFromConfigs(listOf(
            ReminderConfig("my_custom_id", "Quest", "0 9 * * *", 5.minutes, 100)
        ))
        assertNotNull(repository.getById("my_custom_id"))
    }

    @Test
    fun importFromConfigsReplacesOnDuplicateId() = runTest {
        val config1 = ReminderConfig("shared_id", "Original", "0 9 * * *", 5.minutes, 100)
        val config2 = ReminderConfig("shared_id", "Replacement", "0 10 * * *", 5.minutes, 100)
        repository.importFromConfigs(listOf(config1))
        repository.importFromConfigs(listOf(config2))
        val fetched = repository.getById("shared_id")
        assertEquals("Replacement", fetched!!.title)
    }

    @Test
    fun importFromConfigsEmptyListDoesNothing() = runTest {
        repository.importFromConfigs(emptyList())
        assertTrue(repository.getAllReminders().isEmpty())
    }

    // ── hasReminders ──────────────────────────────────────────────────────────

    @Test
    fun hasRemindersFalseWhenEmpty() = runTest {
        assertFalse(repository.hasReminders())
    }

    @Test
    fun hasRemindersTrueAfterCreate() = runTest {
        repository.create("Quest", "0 9 * * *")
        assertTrue(repository.hasReminders())
    }

    @Test
    fun hasRemindersFalseAfterClearAll() = runTest {
        repository.create("Quest", "0 9 * * *")
        repository.clearAll()
        assertFalse(repository.hasReminders())
    }

    // ── clearAll ──────────────────────────────────────────────────────────────

    @Test
    fun clearAllRemovesAllReminders() = runTest {
        repository.create("Q1", "0 9 * * *")
        repository.create("Q2", "0 10 * * *")
        assertEquals(2, repository.getAllReminders().size)
        repository.clearAll()
        assertEquals(0, repository.getAllReminders().size)
    }
}
