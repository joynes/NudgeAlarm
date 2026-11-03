package se.joynes.nudgealarm.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import se.joynes.nudgealarm.database.*
import se.joynes.nudgealarm.service.ReminderService
import se.joynes.nudgealarm.storage.*
import java.io.File

class MainViewModelRecoveryTest {
    @Test fun missingGamesInvalidDocumentsAndEmptyExportsFailWithoutLosingQuests(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = NagDatabase.getInstance(app).reminderDao()
        val previous = dao.getAll()
        val state = AppStateStore(app)
        val previousId = state.getCurrentGameId(); val previousName = state.getCurrentGameName()
        val wasRunning = state.wasServiceRunning()
        val stores = mutableListOf<ViewModelStore>()
        fun model(): MainViewModel {
            lateinit var vm: MainViewModel
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val store = ViewModelStore().also { stores.add(it) }
                vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[MainViewModel::class.java]
            }
            return vm
        }
        suspend fun await(check: suspend () -> Boolean) = withTimeout(15000) { while (!check()) delay(25) }
        val log = EventLogStore(app)
        val missing = Uri.parse("content://${app.packageName}.fileprovider/no-such-root/missing.yaml")
        val output = File.createTempFile("empty-export", ".yaml", app.cacheDir)
        val broken = File(app.filesDir, "game_recovery_broken.yaml")
        try {
            app.stopService(Intent(app, ReminderService::class.java)); await { !ReminderService.isRunning }
            dao.deleteAll(); state.setCurrentGame("recovery_missing", null); state.setServiceWasRunning(false)
            val vm = model()
            await { log.getAll().any { it.message.contains("Saved game found but no reminders") } }
            assertNull(vm.exportToClipboard())
            vm.startService(); vm.saveToFile(Uri.fromFile(output))
            await { log.getAll().any { it.message.contains("No quests to save") } }
            assertEquals(0, output.length())
            vm.loadPreset("does_not_exist")
            await { log.getAll().any { it.message.contains("Unknown preset: does_not_exist") } }
            val empty = SavedGame("recovery_missing", "Empty recovered game", 0, false)
            vm.loadSavedGame(empty)
            await { vm.uiState.value.currentGameName == empty.name }
            assertEquals(0, vm.uiState.value.totalRulesCount)
            broken.writeText("this is not valid YAML: [")
            vm.loadSavedGame(SavedGame("recovery_broken", "Broken", 1, false))
            await { log.getAll().any { it.message.contains("Failed to load game:") } }
            val rule = ReminderEntity("recovery_quest", "Keep this quest", "0 9 * * *")
            dao.insert(rule)
            assertTrue(vm.importFromClipboard("reminders: []").isFailure)
            assertTrue(vm.importFromClipboard("not: [valid").isFailure)
            assertEquals(rule, dao.getById(rule.id))
            vm.saveToFile(missing)
            await { log.getAll().any { it.message.contains("Failed to save:") } }
            vm.loadFromFile(missing)
            await { log.getAll().any { it.message.contains("Failed to load:") } }
            val exported = CompletableDeferred<Result<Int>>()
            vm.exportAllData(missing) { exported.complete(it) }
            assertTrue(withTimeout(15000) { exported.await() }.isFailure)
            val imported = CompletableDeferred<Result<Int>>()
            vm.importAllData(missing) { imported.complete(it) }
            assertTrue(withTimeout(15000) { imported.await() }.isFailure)
            assertEquals(rule, dao.getById(rule.id))
            vm.clearEventLog()
            await { vm.uiState.value.events.any { it.message.contains("Event log cleared") } }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { stores.forEach { it.clear() } }
            app.stopService(Intent(app, ReminderService::class.java)); await { !ReminderService.isRunning }
            dao.deleteAll(); dao.insertAll(previous)
            state.setCurrentGame(previousId, previousName); state.setServiceWasRunning(wasRunning)
            output.delete(); broken.delete()
        }
    }
}
