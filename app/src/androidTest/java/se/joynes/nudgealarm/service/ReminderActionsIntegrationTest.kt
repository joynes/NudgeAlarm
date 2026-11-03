package se.joynes.nudgealarm.service

import android.app.Application
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import se.joynes.nudgealarm.MainActivity
import se.joynes.nudgealarm.database.*
import se.joynes.nudgealarm.storage.AppStateStore
import se.joynes.nudgealarm.storage.SettingsStore
import se.joynes.nudgealarm.ui.MainViewModel

class ReminderActionsIntegrationTest {
    @Test fun serviceActionsCompleteSnoozeCancelAndRecordBulkOutcomes() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = NagDatabase.getInstance(app)
        val reminders = ReminderRepository(db.reminderDao())
        val nags = NagRepository(db.nagStateDao())
        val settings = SettingsStore(app)
        val quiet = settings.quietMode
        settings.quietMode = true
        AppStateStore(app).clear()
        suspend fun await(check: suspend () -> Boolean) = withTimeout(15000) {
            while (!check()) delay(25)
        }
        val ids = (1..5).map { "service_regression_$it" }
        val now = System.currentTimeMillis() / 60000 * 60000
        try {
            db.reminderDao().insertAll(ids.map { ReminderEntity(it, "Test $it", "once:$now", sticky = true) })
            val keys = ids.associateWith { id -> nags.tryFire(reminders.getById(id)!!.toReminderConfig(), now).second }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: MainViewModel
                scenario.onActivity { model = ViewModelProvider(it)[MainViewModel::class.java] }
                model.startServiceFromDatabase()
                await { ReminderService.isRunning && ReminderService.currentConfig?.reminders?.any { it.id == ids[0] } == true }
                model.snoozeReminder(ids[0], 15)
                await { nags.getByKey(keys.getValue(ids[0]))?.status == NagStatus.SNOOZED.name }
                assertTrue(nags.getByKey(keys.getValue(ids[0]))!!.snoozedUntil!! > System.currentTimeMillis())
                model.markReminderDone(ids[1])
                await { nags.getByKey(keys.getValue(ids[1]))?.status == NagStatus.COMPLETED.name }
                await { reminders.getById(ids[1]) == null } // One-time quest is removed after completion.
                model.cancelReminder(ids[2])
                await { nags.getByKey(keys.getValue(ids[2]))?.status == NagStatus.CANCELLED.name }
                model.markAllRemindersDone(listOf(ids[0], ids[3]))
                await { listOf(ids[0], ids[3]).all { nags.getByKey(keys.getValue(it))?.status == NagStatus.COMPLETED.name } }
                model.cancelAllReminders(listOf(ids[4]))
                await { nags.getByKey(keys.getValue(ids[4]))?.status == NagStatus.CANCELLED.name }
                val history = AnalyticsRepository(db.nagHistoryDao()).getHistory(0, System.currentTimeMillis() + 1000)
                assertTrue(history.any { it.ruleId == ids[1] && it.outcome == NagStatus.COMPLETED.name })
                assertTrue(history.any { it.ruleId == ids[4] && it.outcome == NagStatus.CANCELLED.name })
                model.reloadConfig()
                model.stopService()
                await { !ReminderService.isRunning }
            }
        } finally {
            app.startService(Intent(app, ReminderService::class.java).setAction(ReminderService.ACTION_STOP))
            await { !ReminderService.isRunning }
            ids.forEach { db.reminderDao().deleteById(it); db.nagStateDao().deleteByRuleId(it) }
            AppStateStore(app).clear()
            settings.quietMode = quiet
        }
    }
}
