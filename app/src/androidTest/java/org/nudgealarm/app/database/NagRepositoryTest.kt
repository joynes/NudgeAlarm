package org.nudgealarm.app.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nudgealarm.app.core.config.ReminderConfig
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class NagRepositoryTest {

    private lateinit var database: NagDatabase
    private lateinit var repository: NagRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NagDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = NagRepository(database.nagStateDao())
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun createTestRule(
        id: String = "test_rule",
        title: String = "Test Title"
    ): ReminderConfig {
        return ReminderConfig(
            id = id,
            title = title,
            schedule = "0 9 * * *",
            nagInterval = 5.minutes,
            maxNags = 100
        )
    }

    @Test
    fun tryFireCreatesNewOccurrence() = runTest {
        val rule = createTestRule()
        val scheduledTime = 1706684400000L

        val (isNew, key) = repository.tryFire(rule, scheduledTime)

        assertTrue(isNew)
        assertEquals("test_rule@1706684400000", key)

        val retrieved = repository.getByKey(key)
        assertNotNull(retrieved)
        assertEquals(rule.id, retrieved?.ruleId)
        assertEquals(rule.title, retrieved?.title)
        assertEquals(NagStatus.ACTIVE.name, retrieved?.status)
    }

    @Test
    fun tryFireReturnsFalseForDuplicate() = runTest {
        val rule = createTestRule()
        val scheduledTime = 1706684400000L

        val (isNew1, _) = repository.tryFire(rule, scheduledTime)
        assertTrue(isNew1)

        val (isNew2, _) = repository.tryFire(rule, scheduledTime)
        assertFalse(isNew2) // Same occurrence, should be duplicate
    }

    @Test
    fun tryFireAllowsDifferentScheduledTimes() = runTest {
        val rule = createTestRule()

        val (isNew1, key1) = repository.tryFire(rule, 1000L)
        val (isNew2, key2) = repository.tryFire(rule, 2000L)

        assertTrue(isNew1)
        assertTrue(isNew2)
        assertNotEquals(key1, key2)
    }

    @Test
    fun hasAlreadyFired() = runTest {
        val rule = createTestRule()
        val scheduledTime = 1706684400000L

        assertFalse(repository.hasAlreadyFired(rule.id, scheduledTime))

        repository.tryFire(rule, scheduledTime)

        assertTrue(repository.hasAlreadyFired(rule.id, scheduledTime))
    }

    @Test
    fun markDone() = runTest {
        val rule = createTestRule()
        val (_, key) = repository.tryFire(rule, 1000L)

        repository.markDone(key)

        val retrieved = repository.getByKey(key)
        assertEquals(NagStatus.COMPLETED.name, retrieved?.status)
        assertTrue(repository.isDone(key))
    }

    @Test
    fun markDoneByRuleId() = runTest {
        val rule = createTestRule()
        repository.tryFire(rule, 1000L)

        val result = repository.markDoneByRuleId(rule.id)

        assertNotNull(result)
        assertTrue(repository.isDone(result!!.occurrenceKey))
    }

    @Test
    fun markDoneByRuleIdReturnsNullIfNotFound() = runTest {
        val key = repository.markDoneByRuleId("nonexistent_rule")
        assertNull(key)
    }

    @Test
    fun snooze() = runTest {
        val rule = createTestRule()
        val (_, key) = repository.tryFire(rule, 1000L)

        val snoozedUntil = System.currentTimeMillis() + 300000
        repository.snooze(key, snoozedUntil)

        assertTrue(repository.isSnoozed(key))
        val retrieved = repository.getByKey(key)
        assertEquals(snoozedUntil, retrieved?.snoozedUntil)
    }

    @Test
    fun snoozeByRuleId() = runTest {
        val rule = createTestRule()
        repository.tryFire(rule, 1000L)

        val key = repository.snoozeByRuleId(rule.id, 5.minutes)

        assertNotNull(key)
        assertTrue(repository.isSnoozed(key!!))
    }

    @Test
    fun clearSnooze() = runTest {
        val rule = createTestRule()
        val (_, key) = repository.tryFire(rule, 1000L)
        repository.snooze(key, System.currentTimeMillis() + 300000)

        assertTrue(repository.isSnoozed(key))

        repository.clearSnooze(key)

        assertFalse(repository.isSnoozed(key))
        val retrieved = repository.getByKey(key)
        assertEquals(NagStatus.ACTIVE.name, retrieved?.status)
        assertNull(retrieved?.snoozedUntil)
    }

    @Test
    fun incrementNag() = runTest {
        val rule = createTestRule()
        val (_, key) = repository.tryFire(rule, 1000L)

        val before = repository.getByKey(key)
        assertEquals(1, before?.nagCount)

        repository.incrementNag(key)

        val after = repository.getByKey(key)
        assertEquals(2, after?.nagCount)
    }

    @Test
    fun markExpired() = runTest {
        val rule = createTestRule()
        val (_, key) = repository.tryFire(rule, 1000L)

        repository.markExpired(key)

        assertTrue(repository.isDone(key))
        val retrieved = repository.getByKey(key)
        assertEquals(NagStatus.EXPIRED.name, retrieved?.status)
    }

    @Test
    fun isRuleActive() = runTest {
        val rule = createTestRule()

        assertFalse(repository.isRuleActive(rule.id))

        repository.tryFire(rule, 1000L)

        assertTrue(repository.isRuleActive(rule.id))
    }

    @Test
    fun isRuleActiveReturnsFalseAfterDone() = runTest {
        val rule = createTestRule()
        val (_, key) = repository.tryFire(rule, 1000L)

        assertTrue(repository.isRuleActive(rule.id))

        repository.markDone(key)

        assertFalse(repository.isRuleActive(rule.id))
    }

    @Test
    fun getActiveOccurrenceKeyForRule() = runTest {
        val rule = createTestRule()
        val (_, expectedKey) = repository.tryFire(rule, 1000L)

        val key = repository.getActiveOccurrenceKeyForRule(rule.id)

        assertEquals(expectedKey, key)
    }

    @Test
    fun getActiveOccurrenceKeyForRuleReturnsNullIfNotActive() = runTest {
        val rule = createTestRule()

        assertNull(repository.getActiveOccurrenceKeyForRule(rule.id))

        val (_, key) = repository.tryFire(rule, 1000L)
        repository.markDone(key)

        assertNull(repository.getActiveOccurrenceKeyForRule(rule.id))
    }

    @Test
    fun getActiveNags() = runTest {
        val rule1 = createTestRule("rule1", "Title 1")
        val rule2 = createTestRule("rule2", "Title 2")
        val rule3 = createTestRule("rule3", "Title 3")

        repository.tryFire(rule1, 1000L)
        val (_, key2) = repository.tryFire(rule2, 2000L)
        repository.tryFire(rule3, 3000L)

        // Mark one as completed
        repository.markDone(key2)

        val activeNags = repository.getActiveNags()
        assertEquals(2, activeNags.size)
        assertTrue(activeNags.any { it.ruleId == "rule1" })
        assertFalse(activeNags.any { it.ruleId == "rule2" })
        assertTrue(activeNags.any { it.ruleId == "rule3" })
    }

    @Test
    fun cleanup() = runTest {
        val rule = createTestRule()

        // Create old completed nag
        val (_, oldKey) = repository.tryFire(rule, 1000L)
        repository.markDone(oldKey)

        // Manually update triggeredAt to be old (hack for testing)
        val oldEntity = repository.getByKey(oldKey)!!
        database.nagStateDao().update(oldEntity.copy(triggeredAt = 1000L))

        // Create recent nag
        val (_, recentKey) = repository.tryFire(rule, 2000L)

        // Cleanup with cutoff at current time - 1 day
        repository.cleanup(keepDurationMs = 1000L) // Very short for testing

        // Old should be deleted, recent should remain
        assertNull(repository.getByKey(oldKey))
        assertNotNull(repository.getByKey(recentKey))
    }

    @Test
    fun clearAll() = runTest {
        repository.tryFire(createTestRule("rule1"), 1000L)
        repository.tryFire(createTestRule("rule2"), 2000L)
        repository.tryFire(createTestRule("rule3"), 3000L)

        assertEquals(3, repository.getActiveNags().size)

        repository.clearAll()

        assertEquals(0, repository.getActiveNags().size)
    }

    @Test
    fun concurrentDuplicateHandling() = runTest {
        // Simulate what happens when scheduler polls twice in quick succession
        val rule = createTestRule()
        val scheduledTime = 1706684400000L

        // Both calls happen "at the same time"
        val results = listOf(
            repository.tryFire(rule, scheduledTime),
            repository.tryFire(rule, scheduledTime)
        )

        // Exactly one should succeed
        val newCount = results.count { it.first }
        assertEquals(1, newCount)

        // Only one entry in database
        val activeNags = repository.getActiveNags()
        assertEquals(1, activeNags.size)
    }

    // ── markCancelled / markCancelledByRuleId ─────────────────────────────────

    @Test
    fun markCancelled() = runTest {
        val rule = createTestRule()
        val (_, key) = repository.tryFire(rule, 1000L)

        repository.markCancelled(key)

        val fetched = repository.getByKey(key)
        assertEquals(NagStatus.CANCELLED.name, fetched?.status)
        assertFalse(repository.isRuleActive(rule.id))
    }

    @Test
    fun markCancelledByRuleIdReturnsActiveNag() = runTest {
        val rule = createTestRule()
        repository.tryFire(rule, 1000L)

        val result = repository.markCancelledByRuleId(rule.id)

        assertNotNull(result)
        val fetched = repository.getByKey(result!!.occurrenceKey)
        assertEquals(NagStatus.CANCELLED.name, fetched?.status)
    }

    @Test
    fun markCancelledByRuleIdReturnsNullWhenNoActiveNag() = runTest {
        val result = repository.markCancelledByRuleId("nonexistent_rule")
        assertNull(result)
    }

    @Test
    fun markCancelledByRuleIdDoesNotAffectAlreadyCompletedNag() = runTest {
        val rule = createTestRule()
        val (_, key) = repository.tryFire(rule, 1000L)
        repository.markDone(key)

        // Already completed, not in active nags
        val result = repository.markCancelledByRuleId(rule.id)
        assertNull(result)
    }

    // ── getCompletedOrCancelledRuleIdsToday ───────────────────────────────────

    @Test
    fun getCompletedOrCancelledRuleIdsTodayIncludesCompletedToday() = runTest {
        val rule = createTestRule()
        val (_, key) = repository.tryFire(rule, System.currentTimeMillis())
        repository.markDone(key)

        val result = repository.getCompletedOrCancelledRuleIdsToday()
        assertTrue(result.contains(rule.id))
    }

    @Test
    fun getCompletedOrCancelledRuleIdsTodayIncludesCancelledToday() = runTest {
        val rule = createTestRule()
        val (_, key) = repository.tryFire(rule, System.currentTimeMillis())
        repository.markCancelled(key)

        val result = repository.getCompletedOrCancelledRuleIdsToday()
        assertTrue(result.contains(rule.id))
    }

    @Test
    fun getCompletedOrCancelledRuleIdsTodayExcludesActiveNags() = runTest {
        val rule = createTestRule()
        repository.tryFire(rule, System.currentTimeMillis())

        val result = repository.getCompletedOrCancelledRuleIdsToday()
        assertFalse(result.contains(rule.id))
    }

    @Test
    fun getCompletedOrCancelledRuleIdsTodayExcludesOldEntries() = runTest {
        val rule = createTestRule()
        val (_, key) = repository.tryFire(rule, 1000L)
        repository.markDone(key)

        // Back-date the triggeredAt to 25 hours ago (before today's midnight)
        val yesterday = System.currentTimeMillis() - (25 * 60 * 60 * 1000L)
        database.nagStateDao().update(repository.getByKey(key)!!.copy(triggeredAt = yesterday))

        val result = repository.getCompletedOrCancelledRuleIdsToday()
        assertFalse(result.contains(rule.id))
    }

    @Test
    fun getCompletedOrCancelledRuleIdsTodayReturnsEmptyWhenNone() = runTest {
        assertTrue(repository.getCompletedOrCancelledRuleIdsToday().isEmpty())
    }

    // ── createCancelledEntry ──────────────────────────────────────────────────

    @Test
    fun createCancelledEntryCreatesEntryNotInActiveNags() = runTest {
        repository.createCancelledEntry("my_rule", "My Quest")

        // Should not appear in active nags
        assertFalse(repository.isRuleActive("my_rule"))
        assertEquals(0, repository.getActiveNags().size)
    }

    @Test
    fun createCancelledEntryAppearsInCancelledTodaySet() = runTest {
        repository.createCancelledEntry("my_rule", "My Quest")

        val cancelled = repository.getCompletedOrCancelledRuleIdsToday()
        assertTrue(cancelled.contains("my_rule"))
    }

    @Test
    fun createCancelledEntryDoesNotAffectOtherActiveNags() = runTest {
        val rule = createTestRule()
        repository.tryFire(rule, System.currentTimeMillis())

        repository.createCancelledEntry("other_rule", "Other Quest")

        // Original rule still active
        assertTrue(repository.isRuleActive(rule.id))
        assertEquals(1, repository.getActiveNags().size)
    }

    @Test
    fun createCancelledEntryWithNagCountZero() = runTest {
        repository.createCancelledEntry("r1", "Quest Title")
        // getCompletedOrCancelledRuleIdsToday queries the DAO directly;
        // just verify it doesn't throw and returns the entry
        val cancelled = repository.getCompletedOrCancelledRuleIdsToday()
        assertTrue(cancelled.contains("r1"))
    }
}
