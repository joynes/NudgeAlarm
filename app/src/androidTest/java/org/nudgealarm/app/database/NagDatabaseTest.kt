package org.nudgealarm.app.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NagDatabaseTest {

    private lateinit var database: NagDatabase
    private lateinit var dao: NagStateDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NagDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.nagStateDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun createTestEntity(
        ruleId: String = "test_rule",
        scheduledTime: Long = 1000L,
        status: String = NagStatus.ACTIVE.name
    ): NagStateEntity {
        return NagStateEntity(
            occurrenceKey = NagStateEntity.createOccurrenceKey(ruleId, scheduledTime),
            ruleId = ruleId,
            scheduledTime = scheduledTime,
            title = "Test Title",
            triggeredAt = scheduledTime + 100,
            nagCount = 1,
            maxNags = 100,
            nagIntervalMs = 300000L,
            status = status,
            snoozedUntil = null,
            lastNagAt = scheduledTime + 100
        )
    }

    @Test
    fun insertAndRetrieve() = runTest {
        val entity = createTestEntity()
        val result = dao.insertIfNotExists(entity)

        assertTrue(result > 0) // Insert succeeded

        val retrieved = dao.getByKey(entity.occurrenceKey)
        assertNotNull(retrieved)
        assertEquals(entity.ruleId, retrieved?.ruleId)
        assertEquals(entity.title, retrieved?.title)
    }

    @Test
    fun insertDuplicateReturnsMinusOne() = runTest {
        val entity = createTestEntity()

        val firstInsert = dao.insertIfNotExists(entity)
        assertTrue(firstInsert > 0)

        val secondInsert = dao.insertIfNotExists(entity)
        assertEquals(-1L, secondInsert) // Duplicate ignored
    }

    @Test
    fun getActiveNagsReturnsOnlyActiveAndSnoozed() = runTest {
        val active = createTestEntity("rule1", 1000L, NagStatus.ACTIVE.name)
        val snoozed = createTestEntity("rule2", 2000L, NagStatus.SNOOZED.name)
        val completed = createTestEntity("rule3", 3000L, NagStatus.COMPLETED.name)
        val expired = createTestEntity("rule4", 4000L, NagStatus.EXPIRED.name)

        dao.insertIfNotExists(active)
        dao.insertIfNotExists(snoozed)
        dao.insertIfNotExists(completed)
        dao.insertIfNotExists(expired)

        val activeNags = dao.getActiveNags()
        assertEquals(2, activeNags.size)
        assertTrue(activeNags.any { it.ruleId == "rule1" })
        assertTrue(activeNags.any { it.ruleId == "rule2" })
        assertFalse(activeNags.any { it.ruleId == "rule3" })
        assertFalse(activeNags.any { it.ruleId == "rule4" })
    }

    @Test
    fun markCompleted() = runTest {
        val entity = createTestEntity()
        dao.insertIfNotExists(entity)

        dao.markCompleted(entity.occurrenceKey)

        val retrieved = dao.getByKey(entity.occurrenceKey)
        assertEquals(NagStatus.COMPLETED.name, retrieved?.status)
    }

    @Test
    fun markExpired() = runTest {
        val entity = createTestEntity()
        dao.insertIfNotExists(entity)

        dao.markExpired(entity.occurrenceKey)

        val retrieved = dao.getByKey(entity.occurrenceKey)
        assertEquals(NagStatus.EXPIRED.name, retrieved?.status)
    }

    @Test
    fun snooze() = runTest {
        val entity = createTestEntity()
        dao.insertIfNotExists(entity)

        val snoozedUntil = System.currentTimeMillis() + 300000
        dao.snooze(entity.occurrenceKey, snoozedUntil)

        val retrieved = dao.getByKey(entity.occurrenceKey)
        assertEquals(NagStatus.SNOOZED.name, retrieved?.status)
        assertEquals(snoozedUntil, retrieved?.snoozedUntil)
    }

    @Test
    fun clearSnooze() = runTest {
        val entity = createTestEntity().copy(
            status = NagStatus.SNOOZED.name,
            snoozedUntil = System.currentTimeMillis() + 300000
        )
        dao.insertIfNotExists(entity)

        dao.clearSnooze(entity.occurrenceKey)

        val retrieved = dao.getByKey(entity.occurrenceKey)
        assertEquals(NagStatus.ACTIVE.name, retrieved?.status)
        assertNull(retrieved?.snoozedUntil)
    }

    @Test
    fun incrementNag() = runTest {
        val entity = createTestEntity()
        dao.insertIfNotExists(entity)

        val now = System.currentTimeMillis()
        dao.incrementNag(entity.occurrenceKey, now)

        val retrieved = dao.getByKey(entity.occurrenceKey)
        assertEquals(2, retrieved?.nagCount)
        assertEquals(now, retrieved?.lastNagAt)
    }

    @Test
    fun deleteOlderThan() = runTest {
        val old = createTestEntity("old_rule", 1000L, NagStatus.COMPLETED.name)
        val recent = createTestEntity("recent_rule", 5000L, NagStatus.COMPLETED.name)
        val active = createTestEntity("active_rule", 1000L, NagStatus.ACTIVE.name)

        dao.insertIfNotExists(old.copy(triggeredAt = 1000L))
        dao.insertIfNotExists(recent.copy(triggeredAt = 5000L))
        dao.insertIfNotExists(active.copy(triggeredAt = 1000L))

        // Delete completed/expired older than 3000
        dao.deleteOlderThan(3000L)

        // Old completed should be deleted
        assertNull(dao.getByKey(old.occurrenceKey))
        // Recent completed should remain
        assertNotNull(dao.getByKey(recent.occurrenceKey))
        // Active should remain regardless of age
        assertNotNull(dao.getByKey(active.occurrenceKey))
    }

    @Test
    fun deleteAll() = runTest {
        dao.insertIfNotExists(createTestEntity("rule1", 1000L))
        dao.insertIfNotExists(createTestEntity("rule2", 2000L))
        dao.insertIfNotExists(createTestEntity("rule3", 3000L))

        assertEquals(3, dao.getActiveNags().size)

        dao.deleteAll()

        assertEquals(0, dao.getActiveNags().size)
    }

    @Test
    fun getByRuleId() = runTest {
        // Same rule, different scheduled times
        dao.insertIfNotExists(createTestEntity("my_rule", 1000L))
        dao.insertIfNotExists(createTestEntity("my_rule", 2000L))
        dao.insertIfNotExists(createTestEntity("other_rule", 3000L))

        val results = dao.getByRuleId("my_rule")
        assertEquals(2, results.size)
        assertTrue(results.all { it.ruleId == "my_rule" })
    }

    @Test
    fun existsByKey() = runTest {
        val entity = createTestEntity()

        assertEquals(0, dao.existsByKey(entity.occurrenceKey))

        dao.insertIfNotExists(entity)

        assertEquals(1, dao.existsByKey(entity.occurrenceKey))
    }

    @Test
    fun flowUpdatesOnChange() = runTest {
        val entity = createTestEntity()
        dao.insertIfNotExists(entity)

        // Get initial flow value
        var activeNags = dao.getActiveNagsFlow().first()
        assertEquals(1, activeNags.size)

        // Mark as completed
        dao.markCompleted(entity.occurrenceKey)

        // Flow should now return empty
        activeNags = dao.getActiveNagsFlow().first()
        assertEquals(0, activeNags.size)
    }
}
