package org.nudgealarm.app.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nudgealarm.app.ai.tools.GetQuestTool
import org.nudgealarm.app.ai.tools.GetQuestsByIdsTool
import org.nudgealarm.app.ai.tools.ListActiveQuestsTool
import org.nudgealarm.app.ai.tools.ListQuestsTool
import org.nudgealarm.app.ai.tools.SearchQuestsTool
import org.nudgealarm.app.ai.tools.ToolResult
import org.nudgealarm.app.database.NagDatabase
import org.nudgealarm.app.database.NagRepository
import org.nudgealarm.app.database.ReminderRepository

@RunWith(AndroidJUnit4::class)
class QuestReadToolsTest {

    private lateinit var db: NagDatabase
    private lateinit var reminderRepo: ReminderRepository
    private lateinit var nagRepo: NagRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NagDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        reminderRepo = ReminderRepository(db.reminderDao())
        nagRepo = NagRepository(db.nagStateDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun listQuestsReturnsEmptyWhenNoReminders() = runTest {
        val result = ListQuestsTool(reminderRepo).execute(emptyMap())
        assertTrue(result is ToolResult.Success)
        assertEquals("[]", (result as ToolResult.Success).data)
    }

    @Test
    fun listQuestsReturnsAllReminders() = runTest {
        reminderRepo.create("Morning Run", "0 7 * * 1-5")
        reminderRepo.create("Evening Walk", "0 18 * * *")

        val result = ListQuestsTool(reminderRepo).execute(emptyMap())
        assertTrue(result is ToolResult.Success)
        val json = (result as ToolResult.Success).data
        assertTrue(json.contains("Morning Run"))
        assertTrue(json.contains("Evening Walk"))
    }

    @Test
    fun getQuestReturnsCorrectReminder() = runTest {
        val created = reminderRepo.create("Test Quest", "0 9 * * *")
        val result = GetQuestTool(reminderRepo).execute(mapOf("id" to created.id))
        assertTrue(result is ToolResult.Success)
        val json = (result as ToolResult.Success).data
        assertTrue(json.contains("Test Quest"))
        assertTrue(json.contains(created.id))
    }

    @Test
    fun getQuestReturnsErrorForUnknownId() = runTest {
        val result = GetQuestTool(reminderRepo).execute(mapOf("id" to "nonexistent_id"))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("not found"))
    }

    @Test
    fun getQuestReturnsErrorWhenIdMissing() = runTest {
        val result = GetQuestTool(reminderRepo).execute(emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun searchQuestsFindsMatchingByTitle() = runTest {
        reminderRepo.create("Morning Run", "0 7 * * *")
        reminderRepo.create("Evening Yoga", "0 20 * * *")
        reminderRepo.create("Morning Meditation", "0 8 * * *")

        val result = SearchQuestsTool(reminderRepo).execute(mapOf("query" to "morning"))
        assertTrue(result is ToolResult.Success)
        val json = (result as ToolResult.Success).data
        assertTrue(json.contains("Morning Run"))
        assertTrue(json.contains("Morning Meditation"))
        assertTrue(!json.contains("Evening Yoga"))
    }

    @Test
    fun searchQuestsCaseInsensitive() = runTest {
        reminderRepo.create("Morning Run", "0 7 * * *")
        val result = SearchQuestsTool(reminderRepo).execute(mapOf("query" to "MORNING"))
        assertTrue(result is ToolResult.Success)
        assertTrue((result as ToolResult.Success).data.contains("Morning Run"))
    }

    @Test
    fun searchQuestsReturnsEmptyForNoMatch() = runTest {
        reminderRepo.create("Morning Run", "0 7 * * *")
        val result = SearchQuestsTool(reminderRepo).execute(mapOf("query" to "zzznomatch"))
        assertTrue(result is ToolResult.Success)
        assertEquals("[]", (result as ToolResult.Success).data)
    }

    @Test
    fun getQuestsByIdsReturnsRequestedSubset() = runTest {
        val q1 = reminderRepo.create("Quest One", "0 9 * * *")
        reminderRepo.create("Quest Two", "0 10 * * *")
        val q3 = reminderRepo.create("Quest Three", "0 11 * * *")

        val result = GetQuestsByIdsTool(reminderRepo).execute(mapOf("ids" to listOf(q1.id, q3.id)))
        assertTrue(result is ToolResult.Success)
        val json = (result as ToolResult.Success).data
        assertTrue(json.contains("Quest One"))
        assertTrue(json.contains("Quest Three"))
        assertTrue(!json.contains("Quest Two"))
    }

    @Test
    fun getQuestsByIdsReturnsErrorWhenParamMissing() = runTest {
        val result = GetQuestsByIdsTool(reminderRepo).execute(emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun listActiveQuestsReturnsEmptyWhenNoneActive() = runTest {
        val result = ListActiveQuestsTool(nagRepo).execute(emptyMap())
        assertTrue(result is ToolResult.Success)
        assertEquals("[]", (result as ToolResult.Success).data)
    }
}
