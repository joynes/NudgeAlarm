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
        val ids = (1..14).map { "service_regression_$it" }
        val now = System.currentTimeMillis() / 60000 * 60000
        val future = now + 4 * 3600000
        val futureTime = java.util.Calendar.getInstance().apply { timeInMillis = future }
        val futureCron = "${futureTime.get(java.util.Calendar.MINUTE)} ${futureTime.get(java.util.Calendar.HOUR_OF_DAY)} * * *"
        try {
            db.reminderDao().insertAll(ids.mapIndexed { index, id -> ReminderEntity(id, "Test $id",
                if (index < 5 || index >= 10) "once:$now" else if (index % 2 == 0) "once:$future" else futureCron, sticky = true) })
            val keys = (ids.take(5) + ids.drop(10)).associateWith { id -> nags.tryFire(reminders.getById(id)!!.toReminderConfig(), now).second }
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
                model.markReminderDone(ids[5])
                model.markReminderDone(ids[6])
                await { db.nagStateDao().getAll().count { it.ruleId in ids.slice(5..6) && it.status == NagStatus.COMPLETED.name } == 2 }
                model.cancelReminder(ids[7])
                await { db.nagStateDao().getAll().any { it.ruleId == ids[7] && it.status == NagStatus.CANCELLED.name } }
                model.cancelAllReminders(ids.slice(8..9))
                await { db.nagStateDao().getAll().count { it.ruleId in ids.slice(8..9) && it.status == NagStatus.CANCELLED.name } == 2 }
                model.deleteReminderPermanently(ids[10])
                await { reminders.getById(ids[10]) == null && db.nagStateDao().getAll().none { it.ruleId == ids[10] } }
                // Notifications posted before an app update still carry these action names.
                for ((action, minutes) in listOf(ActionReceiver.ACTION_SNOOZE_1H to 60, ActionReceiver.ACTION_SNOOZE_24H to 1440, ActionReceiver.ACTION_SNOOZE_15M to 15)) {
                    val before = System.currentTimeMillis()
                    ActionReceiver().onReceive(app, Intent(action).putExtra(ActionReceiver.EXTRA_RULE_ID, ids[11]))
                    await { (nags.getByKey(keys.getValue(ids[11]))?.snoozedUntil ?: 0L) in
                        (before + minutes * 60000L)..(before + (minutes + 1) * 60000L) }
                }
                ActionReceiver().onReceive(app, Intent(ActionReceiver.ACTION_DONE).putExtra(ActionReceiver.EXTRA_RULE_ID, ids[11]))
                await { nags.getByKey(keys.getValue(ids[11]))?.status == NagStatus.COMPLETED.name }
                app.startService(Intent(app, ReminderService::class.java).setAction(ReminderService.ACTION_DONE_ALL_FROM_UI)
                    .putExtra(ReminderService.EXTRA_RULE_IDS, arrayOf(ids[12])))
                await { nags.getByKey(keys.getValue(ids[12]))?.status == NagStatus.COMPLETED.name }
                app.startService(Intent(app, ReminderService::class.java).setAction(ReminderService.ACTION_CANCEL_ALL_FROM_UI)
                    .putExtra(ReminderService.EXTRA_RULE_IDS, arrayOf(ids[13])))
                await { nags.getByKey(keys.getValue(ids[13]))?.status == NagStatus.CANCELLED.name }
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
