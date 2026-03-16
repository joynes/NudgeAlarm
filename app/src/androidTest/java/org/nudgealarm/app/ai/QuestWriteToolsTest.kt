package org.nudgealarm.app.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nudgealarm.app.ai.tools.CreateQuestTool
import org.nudgealarm.app.ai.tools.CreateQuestsBatchTool
import org.nudgealarm.app.ai.tools.DeleteQuestTool
import org.nudgealarm.app.ai.tools.DeleteQuestsBatchTool
import org.nudgealarm.app.ai.tools.ToolResult
import org.nudgealarm.app.ai.tools.UpdateQuestTool
import org.nudgealarm.app.ai.tools.UpdateQuestsBatchTool
import org.nudgealarm.app.database.NagDatabase
import org.nudgealarm.app.database.ReminderRepository

@RunWith(AndroidJUnit4::class)
class QuestWriteToolsTest {

    private lateinit var db: NagDatabase
    private lateinit var reminderRepo: ReminderRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NagDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        reminderRepo = ReminderRepository(db.reminderDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    // ── CreateQuestTool ──────────────────────────────────────────────────────

    @Test
    fun createQuestCreatesReminderInDatabase() = runTest {
        val result = CreateQuestTool(reminderRepo).execute(
            mapOf("title" to "Morning Run", "schedule" to "0 7 * * 1-5")
        )
        assertTrue(result is ToolResult.Success)
        val all = reminderRepo.getAllReminders()
        assertEquals(1, all.size)
        assertEquals("Morning Run", all[0].title)
        assertEquals("0 7 * * 1-5", all[0].schedule)
    }

    @Test
    fun createQuestUsesDefaultNagInterval() = runTest {
        CreateQuestTool(reminderRepo).execute(
            mapOf("title" to "Run", "schedule" to "0 9 * * *")
        )
        val quest = reminderRepo.getAllReminders().first()
        assertEquals(5, quest.nagIntervalMinutes)
        assertEquals(100, quest.maxNags)
    }

    @Test
    fun createQuestAcceptsCustomInterval() = runTest {
        CreateQuestTool(reminderRepo).execute(
            mapOf("title" to "Run", "schedule" to "0 9 * * *", "nagIntervalMinutes" to 15, "maxNags" to 3)
        )
        val quest = reminderRepo.getAllReminders().first()
        assertEquals(15, quest.nagIntervalMinutes)
        assertEquals(3, quest.maxNags)
    }

    @Test
    fun createQuestReturnsErrorWhenTitleMissing() = runTest {
        val result = CreateQuestTool(reminderRepo).execute(mapOf("schedule" to "0 9 * * *"))
        assertTrue(result is ToolResult.Error)
        assertEquals(0, reminderRepo.getAllReminders().size)
    }

    @Test
    fun createQuestReturnsErrorWhenScheduleMissing() = runTest {
        val result = CreateQuestTool(reminderRepo).execute(mapOf("title" to "Run"))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun createQuestIsWriteTool() {
        assertTrue(CreateQuestTool(reminderRepo).isWriteTool)
    }

    // ── UpdateQuestTool ──────────────────────────────────────────────────────

    @Test
    fun updateQuestChangesTitle() = runTest {
        val created = reminderRepo.create("Old Title", "0 9 * * *")
        val result = UpdateQuestTool(reminderRepo).execute(
            mapOf("id" to created.id, "title" to "New Title")
        )
        assertTrue(result is ToolResult.Success)
        val updated = reminderRepo.getById(created.id)
        assertEquals("New Title", updated?.title)
    }

    @Test
    fun updateQuestChangesSchedule() = runTest {
        val created = reminderRepo.create("Run", "0 9 * * *")
        UpdateQuestTool(reminderRepo).execute(
            mapOf("id" to created.id, "schedule" to "0 18 * * 1-5")
        )
        val updated = reminderRepo.getById(created.id)
        assertEquals("0 18 * * 1-5", updated?.schedule)
    }

    @Test
    fun updateQuestTogglesEnabled() = runTest {
        val created = reminderRepo.create("Run", "0 9 * * *")
        assertTrue(created.enabled)
        UpdateQuestTool(reminderRepo).execute(
            mapOf("id" to created.id, "enabled" to false)
        )
        val updated = reminderRepo.getById(created.id)
        assertEquals(false, updated?.enabled)
    }

    @Test
    fun updateQuestPreservesUnchangedFields() = runTest {
        val created = reminderRepo.create("Run", "0 9 * * *", nagIntervalMinutes = 10, maxNags = 5)
        UpdateQuestTool(reminderRepo).execute(
            mapOf("id" to created.id, "title" to "New Title")
        )
        val updated = reminderRepo.getById(created.id)
        assertEquals(10, updated?.nagIntervalMinutes)
        assertEquals(5, updated?.maxNags)
        assertEquals("0 9 * * *", updated?.schedule)
    }

    @Test
    fun updateQuestReturnsErrorForUnknownId() = runTest {
        val result = UpdateQuestTool(reminderRepo).execute(
            mapOf("id" to "ghost_id", "title" to "X")
        )
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun updateQuestReturnsErrorWhenIdMissing() = runTest {
        val result = UpdateQuestTool(reminderRepo).execute(mapOf("title" to "X"))
        assertTrue(result is ToolResult.Error)
    }

    // ── DeleteQuestTool ──────────────────────────────────────────────────────

    @Test
    fun deleteQuestRemovesFromDatabase() = runTest {
        val created = reminderRepo.create("To Delete", "0 9 * * *")
        val result = DeleteQuestTool(reminderRepo).execute(mapOf("id" to created.id))
        assertTrue(result is ToolResult.Success)
        assertNull(reminderRepo.getById(created.id))
    }

    @Test
    fun deleteQuestReturnsErrorWhenIdMissing() = runTest {
        val result = DeleteQuestTool(reminderRepo).execute(emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun deleteQuestIsWriteTool() {
        assertTrue(DeleteQuestTool(reminderRepo).isWriteTool)
    }

    // ── CreateQuestsBatchTool ────────────────────────────────────────────────

    @Test
    fun createQuestsBatchCreatesMultiple() = runTest {
        val result = CreateQuestsBatchTool(reminderRepo).execute(
            mapOf("quests" to listOf(
                mapOf("title" to "Quest A", "schedule" to "0 9 * * *"),
                mapOf("title" to "Quest B", "schedule" to "0 10 * * *"),
                mapOf("title" to "Quest C", "schedule" to "0 11 * * *")
            ))
        )
        assertTrue(result is ToolResult.Success)
        assertEquals(3, reminderRepo.getAllReminders().size)
    }

    @Test
    fun createQuestsBatchReturnsErrorForMissingParam() = runTest {
        val result = CreateQuestsBatchTool(reminderRepo).execute(emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    // ── UpdateQuestsBatchTool ────────────────────────────────────────────────

    @Test
    fun updateQuestsBatchUpdatesMultiple() = runTest {
        val q1 = reminderRepo.create("Q1", "0 9 * * *")
        val q2 = reminderRepo.create("Q2", "0 10 * * *")
        val result = UpdateQuestsBatchTool(reminderRepo).execute(
            mapOf("updates" to listOf(
                mapOf("id" to q1.id, "title" to "Q1 Updated"),
                mapOf("id" to q2.id, "enabled" to false)
            ))
        )
        assertTrue(result is ToolResult.Success)
        assertEquals("Q1 Updated", reminderRepo.getById(q1.id)?.title)
        assertEquals(false, reminderRepo.getById(q2.id)?.enabled)
    }

    // ── DeleteQuestsBatchTool ────────────────────────────────────────────────

    @Test
    fun deleteQuestsBatchDeletesMultiple() = runTest {
        val q1 = reminderRepo.create("Q1", "0 9 * * *")
        val q2 = reminderRepo.create("Q2", "0 10 * * *")
        val q3 = reminderRepo.create("Q3", "0 11 * * *")

        val result = DeleteQuestsBatchTool(reminderRepo).execute(
            mapOf("ids" to listOf(q1.id, q2.id))
        )
        assertTrue(result is ToolResult.Success)
        assertNull(reminderRepo.getById(q1.id))
        assertNull(reminderRepo.getById(q2.id))
        assertNotNull(reminderRepo.getById(q3.id)) // q3 should still exist
    }

    @Test
    fun deleteQuestsBatchReturnsErrorForMissingParam() = runTest {
        val result = DeleteQuestsBatchTool(reminderRepo).execute(emptyMap())
        assertTrue(result is ToolResult.Error)
    }
}
