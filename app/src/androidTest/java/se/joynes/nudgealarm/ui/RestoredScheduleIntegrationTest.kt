package se.joynes.nudgealarm.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import se.joynes.nudgealarm.MainActivity
import se.joynes.nudgealarm.database.*
import se.joynes.nudgealarm.service.ReminderService
import se.joynes.nudgealarm.storage.*
import java.util.Calendar

class RestoredScheduleIntegrationTest {
    @Test fun reopeningRestartsTheSavedGameAndShowsSnoozesBeyondTodayWhileExpiringOldQuests(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = NagDatabase.getInstance(app)
        val previous = db.reminderDao().getAll()
        val state = AppStateStore(app)
        val id = state.getCurrentGameId(); val name = state.getCurrentGameName(); val running = state.wasServiceRunning()
        val settings = SettingsStore(app)
        val original = SettingsTransferStore(app).snapshot()
        val quiet = settings.quietMode; val quietUntil = settings.quietModeUntil
        val ids = listOf("restore_active", "restore_snoozed", "restore_once", "restore_cron", "restore_stale")
        val secondStore = ViewModelStore()
        suspend fun await(check: suspend () -> Boolean) = withTimeout(15000) { while (!check()) delay(25) }
        try {
            app.stopService(Intent(app, ReminderService::class.java)); await { !ReminderService.isRunning }
            db.reminderDao().deleteAll()
            settings.quietMode = true; settings.oldReminderRetentionMinutes = 15
            val now = System.currentTimeMillis()
            val cronTime = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 4) }
            val cron = "${cronTime.get(Calendar.MINUTE)} ${cronTime.get(Calendar.HOUR_OF_DAY)} * * *"
            val end = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            db.reminderDao().insertAll(listOf(
                ReminderEntity(ids[0], "Already active", "once:${now - 180000}", sticky = true),
                ReminderEntity(ids[1], "Paused into tomorrow", "once:$end", sticky = true),
                ReminderEntity(ids[2], "Later today", "once:$end"),
                ReminderEntity(ids[3], "Daily schedule", cron),
                ReminderEntity(ids[4], "Old quest", "once:${now - 3 * 86400000L}", enabled = false)))
            fun nag(ruleId: String, at: Long, status: String = "ACTIVE", until: Long? = null) = NagStateEntity(
                "$ruleId@$at", ruleId, at, ruleId, at, 1, 100, 300000, status, until, now)
            db.nagStateDao().insertIfNotExists(nag(ids[0], now))
            val until = now + 27 * 3600000L
            db.nagStateDao().insertIfNotExists(nag(ids[1], now, "SNOOZED", until))
            state.setCurrentGame("restored_schedule", "Recovered routines"); state.setServiceWasRunning(true)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var vm: MainViewModel
                scenario.onActivity { vm = ViewModelProvider(it)[MainViewModel::class.java] }
                await { ReminderService.isRunning && vm.uiState.value.currentGameName == "Recovered routines" &&
                    vm.uiState.value.todaysSchedule.any { it.ruleId == ids[1] && it.isSnoozed } &&
                    (vm.uiState.value.todaysSchedule.map { it.ruleId } + vm.uiState.value.activeReminders.map { it.ruleId }).containsAll(ids.slice(2..3)) &&
                    vm.uiState.value.nextTriggerTime != null }
                val ui = vm.uiState.value
                assertTrue(ui.activeReminders.any { it.ruleId == ids[0] })
                assertFalse(ui.activeReminders.any { it.ruleId == ids[1] })
                assertEquals(until, ui.todaysSchedule.single { it.ruleId == ids[1] }.scheduledTimeMillis)
                assertTrue((ui.todaysSchedule.map { it.ruleId } + ui.activeReminders.map { it.ruleId }).containsAll(ids.slice(2..3)))
                assertNotNull(ui.nextTriggerTime)
                // A second view model must attach to the existing foreground service.
                lateinit var attached: MainViewModel
                scenario.onActivity {
                    attached = ViewModelProvider(secondStore, ViewModelProvider.AndroidViewModelFactory(app))[MainViewModel::class.java]
                    attached.startPolling()
                }
                await { attached.uiState.value.currentGameName == "Recovered routines" }
                val old = now - 3 * 86400000L
                db.nagStateDao().insertIfNotExists(nag(ids[4], old))
                ReminderService.activeReminders[ids[4]] = ReminderService.ActiveReminder(ids[4], "Old quest", old, occurrenceKey = "${ids[4]}@$old")
                await { db.nagStateDao().getByKey("${ids[4]}@$old")?.status == "EXPIRED" && ids[4] !in ReminderService.activeReminders }
                assertTrue(EventLogStore(app).getAll().any { it.message.contains("Auto-expired 1 stale reminder") })
                vm.stopService(); await { !ReminderService.isRunning }
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { secondStore.clear() }
            app.stopService(Intent(app, ReminderService::class.java)); await { !ReminderService.isRunning }
            ids.forEach { db.nagStateDao().deleteByRuleId(it) }
            db.reminderDao().deleteAll(); db.reminderDao().insertAll(previous)
            state.setCurrentGame(id, name); state.setServiceWasRunning(running)
            SettingsTransferStore(app).apply(original)
            settings.quietMode = quiet; settings.quietModeUntil = quietUntil
        }
    }
}
