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
import se.joynes.nudgealarm.database.*
import se.joynes.nudgealarm.storage.*

class SettingsViewModelIntegrationTest {
    @Test fun settingsPersistAndPlaceErrorsAreRecoverableWithoutLosingQuestLinks() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val transfer = SettingsTransferStore(app)
        val original = transfer.snapshot()
        val db = NagDatabase.getInstance(app)
        val repository = SavedPlaceRepository(db.savedPlaceDao())
        val home = repository.createPlace("ViewModel home", 59.0, 18.0, 250)
        val position = repository.getActiveLocation(home.id)!!
        val store = ViewModelStore()
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        val model = ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory(app))[SettingsViewModel::class.java]
        suspend fun await(check: () -> Boolean) = withTimeout(10000) { while (!check()) delay(25) }
        try {
            model.setOldReminderRetentionMinutes(97)
            model.setAlertOnlyWhenActive(true)
            model.setMinAlertIntervalMinutes(37)
            model.setInteractionGraceMinutes(47)
            model.setInteractionGraceSource("app")
            assertEquals(97, SettingsStore(app).oldReminderRetentionMinutes)
            assertEquals(37, model.uiState.value.minAlertIntervalMinutes)
            assertEquals(47, model.uiState.value.interactionGraceMinutes)
            assertEquals("app", SettingsStore(app).interactionGraceSource)
            assertTrue(model.uiState.value.alertOnlyWhenActive)
            model.setCustomSound("Test sound", AlarmSound.DEFAULT_NOTIFICATION.uri!!)
            assertEquals("Test sound", model.uiState.value.customSoundName)
            model.setAlarmSound(AlarmSound.SILENT)
            assertNull(model.uiState.value.customSoundUri)
            assertNull(SettingsStore(app).customSoundName)
            model.setVibrationEnabled(false)
            assertFalse(model.uiState.value.vibrationEnabled)
            model.previewSound(Uri.EMPTY) // Unavailable audio must fail safely, without crashing.
            model.previewSound(Uri.EMPTY) // Same selection stops preview.
            assertNull(model.uiState.value.currentlyPlayingUri)
            model.stopPreview()
            model.onSettingsImported()
            assertEquals(AlarmSound.SILENT, model.uiState.value.alarmSound)
            model.renamePlace(home.id, "")
            await { model.uiState.value.placeError != null }
            model.renamePlace(home.id, "Renamed home")
            await { model.uiState.value.placeError == null && model.uiState.value.savedPlaces.any { it.place.id == home.id && it.place.name == "Renamed home" } }
            model.updatePosition(home.id, position.id, "Updated", 59.1, 18.1, 300)
            await { model.uiState.value.savedPlaces.any { p -> p.locations.any { it.id == position.id && it.radiusMeters == 300 } } }
            model.addPosition(home.id, "Travel", 36.0, -4.0, 400)
            await { model.uiState.value.savedPlaces.any { it.place.id == home.id && it.locations.size == 2 } }
            model.selectPosition(home.id, position.id)
            await { model.uiState.value.savedPlaces.any { it.place.id == home.id && it.place.activeLocationId == position.id } }
            model.deletePosition(home.id, position.id)
            await { model.uiState.value.savedPlaces.any { it.place.id == home.id && it.locations.size == 1 } }
            model.deletePlace(home.id)
            await { model.uiState.value.savedPlaces.none { it.place.id == home.id } }
        } finally {
            store.clear()
            if (db.savedPlaceDao().getPlace(home.id) != null) repository.deletePlace(home.id)
            transfer.apply(original)
        }
    }
}
