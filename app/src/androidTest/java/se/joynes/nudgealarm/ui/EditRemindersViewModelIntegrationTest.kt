package se.joynes.nudgealarm.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import se.joynes.nudgealarm.database.NagDatabase
import se.joynes.nudgealarm.storage.ConfigLoader
import java.io.File

class EditRemindersViewModelIntegrationTest {
    @Test fun createEditExportAndDeletePreserveTheSavedQuestAndPlaceLink() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val database = NagDatabase.getInstance(application)
        val store = ViewModelStore()
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        val model = ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory(application))[EditRemindersViewModel::class.java]
        val name = "Coverage laundry ${System.nanoTime()}"
        var ruleId: String? = null
        var placeId: String? = null
        val file = File.createTempFile("quest-model", ".yaml", application.cacheDir)
        try {
            withTimeout(10000) { model.uiState.first { !it.isLoading } }
            model.startAddNew()
            assertTrue(model.uiState.value.isAddingNew)
            model.cancelEdit()
            assertFalse(model.uiState.value.isAddingNew)
            model.createSavedPlace(name, 59.0, 18.0, 250)
            val withPlace = withTimeout(10000) { model.uiState.first { state -> state.savedPlaces.any { it.place.name == name } } }
            val place = withPlace.savedPlaces.first { it.place.name == name }
            placeId = place.place.id
            model.startAddNew()
            model.saveReminder(name, "0 9 * * *", 15, 12, true, placeId)
            val created = withTimeout(10000) { model.uiState.first { state -> state.reminders.any { it.title == name } } }.reminders.first { it.title == name }
            ruleId = created.id
            model.startEditById(created.id)
            withTimeout(10000) { model.uiState.first { it.editingReminder?.id == created.id } }
            model.saveReminder("Edited $name", "0 10 * * *", 30, 8, false, placeId)
            val edited = withTimeout(10000) { model.uiState.first { state -> state.reminders.any { it.id == created.id && it.title == "Edited $name" } } }.reminders.first { it.id == created.id }
            assertEquals(30, edited.nagIntervalMinutes)
            assertEquals(8, edited.maxNags)
            assertFalse(edited.sticky)
            assertEquals(placeId, edited.placeId)
            model.toggleEnabled(created.id)
            withTimeout(10000) { model.uiState.first { state -> state.reminders.any { it.id == created.id && !it.enabled } } }
            val exported = CompletableDeferred<Result<Int>>()
            model.exportSelected(Uri.fromFile(file), setOf(created.id), "Test export") { exported.complete(it) }
            assertEquals(1, withTimeout(10000) { exported.await() }.getOrThrow())
            assertEquals(created.id, ConfigLoader(application).loadFromUri(Uri.fromFile(file)).getOrThrow().reminders.single().id)
            val empty = CompletableDeferred<Result<Int>>()
            model.exportSelected(Uri.fromFile(file), emptySet(), "Empty") { empty.complete(it) }
            assertTrue(withTimeout(10000) { empty.await() }.isFailure)
            model.addSavedPosition(place.place.id, "Travel home", 36.0, -4.0, 500)
            withTimeout(10000) { model.uiState.first { state -> state.savedPlaces.any { it.place.id == placeId && it.locations.size == 2 } } }
            val originalPosition = place.locations.single().id
            model.selectSavedPosition(place.place.id, originalPosition)
            withTimeout(10000) { model.uiState.first { state -> state.savedPlaces.any { it.place.id == placeId && it.place.activeLocationId == originalPosition } } }
            model.startEdit(edited)
            assertEquals(created.id, model.uiState.value.editingReminder!!.id)
            model.cancelEdit()
            model.refresh()
            withTimeout(10000) { model.uiState.first { !it.isLoading } }
            model.deleteReminder(created.id)
            withTimeout(10000) { model.uiState.first { state -> state.reminders.none { it.id == created.id } } }
            assertNull(database.reminderDao().getById(created.id))
        } finally {
            store.clear()
            ruleId?.let { database.reminderDao().deleteById(it) }
            placeId?.let { database.savedPlaceDao().deletePlaceAndUnlink(it) }
            file.delete()
        }
    }
}
