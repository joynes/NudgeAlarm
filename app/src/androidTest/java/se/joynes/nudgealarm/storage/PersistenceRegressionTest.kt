package se.joynes.nudgealarm.storage

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PersistenceRegressionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun savedGamesUpdateByIdentityAndRecoverFromCorruptStorage() {
        val prefs = context.getSharedPreferences("saved_games", Context.MODE_PRIVATE)
        val original = prefs.getString("games", null)
        try {
            val store = SavedGamesStore(context)
            store.clear()
            assertTrue(store.getAll().isEmpty())
            val first = SavedGame("one", "Home", 2, false, "/test.yaml", 123)
            store.save(first)
            store.save(first.copy(name = "Renamed", questCount = 3))
            store.save(SavedGame("two", "Work", 1, true))
            assertEquals(2, SavedGamesStore(context).getAll().size)
            assertEquals("Renamed", store.get("one")!!.name)
            assertTrue(store.exists("two"))
            store.delete("one")
            store.delete("missing")
            assertNull(store.get("one"))
            assertFalse(store.exists("one"))
            assertEquals(listOf("two"), store.getAll().map { it.id })
            prefs.edit().putString("games", "broken JSON").commit()
            assertTrue(store.getAll().isEmpty())
            prefs.edit().putString("games", "null").commit()
            assertTrue(store.getAll().isEmpty())
            store.save(first)
            assertEquals(first, store.get("one"))
        } finally { prefs.edit().putString("games", original).commit() }
    }

    @Test fun appStateSurvivesReopeningAndCanBeReset() {
        val store = AppStateStore(context)
        val id = store.getCurrentGameId()
        val name = store.getCurrentGameName()
        val running = store.wasServiceRunning()
        try {
            store.clear()
            assertNull(store.getCurrentGameId())
            assertNull(store.getCurrentGameName())
            assertFalse(store.wasServiceRunning())
            store.setCurrentGame("test", "My routines")
            store.setServiceWasRunning(true)
            assertEquals("test", AppStateStore(context).getCurrentGameId())
            assertEquals("My routines", AppStateStore(context).getCurrentGameName())
            assertTrue(AppStateStore(context).wasServiceRunning())
            store.setServiceWasRunning(false)
            store.setCurrentGame(null, null)
            assertFalse(store.wasServiceRunning())
            assertNull(store.getCurrentGameId())
        } finally { store.setCurrentGame(id, name); store.setServiceWasRunning(running) }
    }

    @Test fun loaderParsesFilesAndReportsMissingAndInvalidInput() {
        val loader = ConfigLoader(context)
        val file = File.createTempFile("config-regression", ".yaml", context.cacheDir)
        try {
            file.writeText("reminders:\n  - id: laundry\n    title: Laundry\n    schedule: '0 9 * * *'\n")
            val config = loader.loadFromUri(Uri.fromFile(file)).getOrThrow()
            assertEquals("laundry", config.reminders.single().id)
            assertEquals(config, loader.loadFromString(file.readText()).getOrThrow())
            file.writeText("reminders: not-an-array")
            assertTrue(loader.loadFromUri(Uri.fromFile(file)).isFailure)
            assertTrue(loader.loadFromString("[").isFailure)
            file.delete()
            assertTrue(loader.loadFromUri(Uri.fromFile(file)).isFailure)
            assertTrue(loader.loadFromUri(Uri.parse("content://invalid.example/missing")).isFailure)
        } finally { file.delete() }
    }
}
