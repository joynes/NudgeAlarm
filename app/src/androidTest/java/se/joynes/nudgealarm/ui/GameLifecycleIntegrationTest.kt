package se.joynes.nudgealarm.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import se.joynes.nudgealarm.MainActivity
import se.joynes.nudgealarm.database.NagDatabase
import se.joynes.nudgealarm.service.ReminderService
import se.joynes.nudgealarm.storage.*

/** End-to-end regression of saved-game operations, using only the isolated debug package. */
class GameLifecycleIntegrationTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = NagDatabase.getInstance(app)
    private suspend fun await(check: suspend () -> Boolean) = withTimeout(15000) {
        while (!check()) delay(25)
    }

    @Test fun newSchemaCanBeRenamedSavedReloadedAndDeleted() = runBlocking {
        val quiet = SettingsStore(app).quietMode
        SettingsStore(app).quietMode = true
        AppStateStore(app).clear()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: MainViewModel
            scenario.onActivity { model = ViewModelProvider(it)[MainViewModel::class.java] }
            try {
                val created = CompletableDeferred<Unit>()
                model.createNewSchema("Lifecycle test") { created.complete(Unit) }
                withTimeout(15000) { created.await() }
                val id = model.uiState.value.currentGameId!!
                assertTrue(model.uiState.value.hasGameLoaded)
                model.renameCurrentGame("   ")
                assertEquals("Lifecycle test", model.uiState.value.currentGameName)
                model.renameCurrentGame("  Renamed routines  ")
                await { model.uiState.value.currentGameName == "Renamed routines" }
                val repository = se.joynes.nudgealarm.database.ReminderRepository(db.reminderDao())
                val rule = repository.create("Laundry \"delicate\"\nSecond line", "0 9 * * *", sticky = true)
                model.updateCurrentGameQuestCount()
                await { SavedGamesStore(app).get(id)?.questCount == 1 }
                model.saveCurrentGame()
                val internalSave = java.io.File(app.filesDir, "game_$id.yaml")
                await { internalSave.exists() && internalSave.length() > 0 }
                assertEquals(rule.title, ConfigLoader(app).loadFromUri(android.net.Uri.fromFile(internalSave)).getOrThrow().reminders.single().title)
                val saved = SavedGamesStore(app).get(id)!!
                assertEquals("Renamed routines", saved.name)
                val exported = model.exportToClipboard()!!
                assertEquals(rule.id, ConfigLoader(app).loadFromString(exported).getOrThrow().reminders.single().id)
                assertTrue(model.importFromClipboard("invalid: yaml").isFailure)
                assertTrue(model.importFromClipboard("reminders: []").isFailure)
                assertEquals(rule.id, db.reminderDao().getAll().single().id)
                db.reminderDao().deleteAll()
                model.loadSavedGame(saved)
                await { db.reminderDao().getById(rule.id) != null }
                assertEquals("Renamed routines", model.uiState.value.currentGameName)
                model.deleteSavedGame(id)
                await { !SavedGamesStore(app).exists(id) }
            } finally {
                app.startService(Intent(app, ReminderService::class.java).setAction(ReminderService.ACTION_STOP))
                await { !ReminderService.isRunning }
                db.reminderDao().deleteAll()
                AppStateStore(app).clear()
                SavedGamesStore(app).clear()
                SettingsStore(app).quietMode = quiet
            }
        }
    }

    @Test fun everyBuiltInPresetProducesLoadableQuestsAndStartsTheMatchingSchedule() = runBlocking {
        AppStateStore(app).clear()
        val settings = SettingsStore(app)
        val quiet = settings.quietMode
        settings.quietMode = true
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: MainViewModel
            scenario.onActivity { model = ViewModelProvider(it)[MainViewModel::class.java] }
            try {
                for (id in listOf("test_1min", "test_hourly", "fitness_focus", "student_life", "self_care", "productivity", "parent_life", "healthy_habits")) {
                    model.loadPreset(id)
                    await { model.uiState.value.currentGameId == id }
                    val quests = db.reminderDao().getAll()
                    assertTrue("$id has no quests", quests.isNotEmpty())
                    assertEquals(quests.size, quests.map { it.id }.distinct().size)
                    val yaml = model.exportToClipboard()!!
                    assertEquals(quests.size, ConfigLoader(app).loadFromString(yaml).getOrThrow().reminders.size)
                    await { ReminderService.isRunning && ReminderService.currentConfig?.reminders?.map { it.id }?.toSet() == quests.map { it.id }.toSet() }
                }
            } finally {
                app.startService(Intent(app, ReminderService::class.java).setAction(ReminderService.ACTION_STOP))
                await { !ReminderService.isRunning }
                db.reminderDao().deleteAll()
                AppStateStore(app).clear()
                SavedGamesStore(app).clear()
                settings.quietMode = quiet
            }
        }
    }
}
