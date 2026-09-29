package se.joynes.nudgealarm.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.joynes.nudgealarm.database.NagDatabase
import se.joynes.nudgealarm.database.SavedPlaceRepository
import se.joynes.nudgealarm.database.SavedPlaceWithLocations
import se.joynes.nudgealarm.notification.ChannelSetup
import se.joynes.nudgealarm.service.ReminderService
import se.joynes.nudgealarm.storage.AlarmSound
import se.joynes.nudgealarm.storage.SettingsStore
import se.joynes.nudgealarm.storage.SoundPreviewPlayer

data class DeviceRingtone(
    val name: String,
    val uri: Uri
)

data class SettingsUiState(
    val alarmSound: AlarmSound = AlarmSound.DEFAULT_ALARM,
    val customSoundUri: Uri? = null,  // For device ringtone selection
    val customSoundName: String? = null,
    val vibrationEnabled: Boolean = true,
    val deviceRingtones: List<DeviceRingtone> = emptyList(),
    val currentlyPlayingUri: Uri? = null,  // Track which sound is playing
    val oldReminderRetentionMinutes: Int = 24 * 60,
    val alertOnlyWhenActive: Boolean = false,
    val minAlertIntervalMinutes: Int = 0,
    val savedPlaces: List<SavedPlaceWithLocations> = emptyList(),
    val linkedReminderCounts: Map<String, Int> = emptyMap(),
    val placeError: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val soundPlayer = SoundPreviewPlayer(application)
    private val savedPlaceRepository = SavedPlaceRepository(NagDatabase.getInstance(application).savedPlaceDao())

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        loadDeviceRingtones()
        refreshPlaces()
    }

    fun refreshPlaces() {
        viewModelScope.launch {
            val (places, counts) = withContext(Dispatchers.IO) {
                val places = savedPlaceRepository.getAll()
                places to places.associate { it.place.id to savedPlaceRepository.linkedReminderCount(it.place.id) }
            }
            _uiState.value = _uiState.value.copy(savedPlaces = places, linkedReminderCounts = counts)
        }
    }

    private fun changePlace(reloadService: Boolean = false, block: suspend () -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { block() } }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(placeError = null)
                    refreshPlaces()
                    if (reloadService && ReminderService.isRunning) {
                        getApplication<Application>().startService(
                            Intent(getApplication(), ReminderService::class.java).apply {
                                action = ReminderService.ACTION_RELOAD
                            }
                        )
                    }
                },
                onFailure = { _uiState.value = _uiState.value.copy(placeError = it.message ?: "Could not update place") }
            )
        }
    }

    fun renamePlace(placeId: String, name: String) = changePlace {
        savedPlaceRepository.renamePlace(placeId, name)
    }

    fun updatePosition(placeId: String, locationId: String, label: String, latitude: Double, longitude: Double, radiusMeters: Int) = changePlace {
        savedPlaceRepository.updatePosition(placeId, locationId, label, latitude, longitude, radiusMeters)
    }

    fun selectPosition(placeId: String, locationId: String) = changePlace {
        savedPlaceRepository.setActiveLocation(placeId, locationId)
    }

    fun deletePosition(placeId: String, locationId: String) = changePlace {
        savedPlaceRepository.deletePosition(placeId, locationId)
    }

    fun deletePlace(placeId: String) = changePlace(reloadService = true) {
        savedPlaceRepository.deletePlace(placeId)
    }

    private fun loadSettings() {
        _uiState.value = _uiState.value.copy(
            alarmSound = settingsStore.alarmSound,
            vibrationEnabled = settingsStore.vibrationEnabled,
            customSoundUri = settingsStore.customSoundUri,
            customSoundName = settingsStore.customSoundName,
            oldReminderRetentionMinutes = settingsStore.oldReminderRetentionMinutes,
            alertOnlyWhenActive = settingsStore.alertOnlyWhenActive,
            minAlertIntervalMinutes = settingsStore.minAlertIntervalMinutes
        )
    }

    fun setOldReminderRetentionMinutes(minutes: Int) {
        settingsStore.oldReminderRetentionMinutes = minutes
        _uiState.value = _uiState.value.copy(oldReminderRetentionMinutes = minutes)
    }

    fun setAlertOnlyWhenActive(enabled: Boolean) {
        settingsStore.alertOnlyWhenActive = enabled
        _uiState.value = _uiState.value.copy(alertOnlyWhenActive = enabled)
    }

    fun setMinAlertIntervalMinutes(minutes: Int) {
        settingsStore.minAlertIntervalMinutes = minutes
        _uiState.value = _uiState.value.copy(minAlertIntervalMinutes = minutes)
    }

    private fun loadDeviceRingtones() {
        val ringtones = AlarmSound.getDeviceRingtones(getApplication())
        _uiState.value = _uiState.value.copy(
            deviceRingtones = ringtones.map { DeviceRingtone(it.first, it.second) }
        )
    }

    fun setAlarmSound(sound: AlarmSound) {
        settingsStore.alarmSound = sound
        settingsStore.customSoundUri = null  // Clear custom when selecting preset
        settingsStore.customSoundName = null
        _uiState.value = _uiState.value.copy(
            alarmSound = sound,
            customSoundUri = null,
            customSoundName = null
        )
        // Recreate the notification channel with new sound
        ChannelSetup.recreateReminderChannel(getApplication())
    }

    fun setCustomSound(name: String, uri: Uri) {
        android.util.Log.d("SettingsViewModel", "setCustomSound: name=$name uri=$uri")
        settingsStore.customSoundUri = uri
        settingsStore.customSoundName = name
        _uiState.value = _uiState.value.copy(
            customSoundUri = uri,
            customSoundName = name
        )
        // Recreate the notification channel with new sound
        android.util.Log.d("SettingsViewModel", "Recreating channel with effectiveUri=${settingsStore.getEffectiveSoundUri()}")
        ChannelSetup.recreateReminderChannel(getApplication())
    }

    fun setVibrationEnabled(enabled: Boolean) {
        settingsStore.vibrationEnabled = enabled
        _uiState.value = _uiState.value.copy(vibrationEnabled = enabled)
        // Recreate the notification channel with new vibration setting
        ChannelSetup.recreateReminderChannel(getApplication())
    }

    fun previewSound(uri: Uri?) {
        if (uri == _uiState.value.currentlyPlayingUri) {
            // Stop if same sound is clicked again
            stopPreview()
        } else {
            _uiState.value = _uiState.value.copy(currentlyPlayingUri = uri)
            soundPlayer.play(uri)
            // Auto-clear after a short delay (sounds usually complete quickly)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                _uiState.value = _uiState.value.copy(currentlyPlayingUri = null)
            }, 3000)
        }
    }

    fun stopPreview() {
        soundPlayer.stop()
        _uiState.value = _uiState.value.copy(currentlyPlayingUri = null)
    }

    override fun onCleared() {
        super.onCleared()
        soundPlayer.stop()
    }
}
