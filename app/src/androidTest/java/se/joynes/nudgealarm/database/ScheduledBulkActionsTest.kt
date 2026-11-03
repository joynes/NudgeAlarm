package se.joynes.nudgealarm.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ScheduledBulkActionsTest {
    @Test fun scheduledBulkActionsAreDurableIdempotentAndLeaveUnselectedQuestsAlone(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NagDatabase::class.java).build()
        try {
            val once = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val rules = listOf(ReminderEntity("once", "Later today", "once:$once"),
                ReminderEntity("cron", "Every day", "59 23 * * *"),
                ReminderEntity("untouched", "Other quest", "0 9 * * *"),
                ReminderEntity("invalid", "Bad legacy schedule", "bad"))
            db.reminderDao().insertAll(rules)
            db.finishPendingQuests(listOf("once", "once", "missing", "invalid"), NagStatus.COMPLETED)
            db.finishPendingQuests(listOf("cron"), NagStatus.CANCELLED)
            db.finishPendingQuests(listOf("once", "cron"), NagStatus.COMPLETED)
            val nags = db.nagStateDao().getAll()
            assertEquals(2, nags.size)
            assertEquals("COMPLETED", nags.single { it.ruleId == "once" }.status)
            assertEquals(once, nags.single { it.ruleId == "once" }.scheduledTime)
            assertEquals("CANCELLED", nags.single { it.ruleId == "cron" }.status)
            val history = AnalyticsRepository(db.nagHistoryDao()).getHistory(0, System.currentTimeMillis() + 1000)
            assertEquals(2, history.size)
            assertEquals(setOf("COMPLETED", "CANCELLED"), history.map { it.outcome }.toSet())
            val repo = NagRepository(db.nagStateDao())
            assertFalse(repo.tryFire(rules[0].toReminderConfig(), once).first)
            assertFalse(repo.tryFire(rules[1].toReminderConfig(), once).first)
            assertNull(db.nagStateDao().getAll().find { it.ruleId == "untouched" })
        } finally { db.close() }
    }
}
