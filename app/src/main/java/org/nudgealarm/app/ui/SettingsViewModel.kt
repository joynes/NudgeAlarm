package org.nudgealarm.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.nudgealarm.app.notification.ChannelSetup
import org.nudgealarm.app.storage.AlarmSound
import org.nudgealarm.app.storage.SettingsStore
import org.nudgealarm.app.storage.SoundPreviewPlayer

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
    val staleTaskThresholdDays: Int = 1
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val soundPlayer = SoundPreviewPlayer(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        loadDeviceRingtones()
    }

    private fun loadSettings() {
        _uiState.value = _uiState.value.copy(
            alarmSound = settingsStore.alarmSound,
            vibrationEnabled = settingsStore.vibrationEnabled,
            customSoundUri = settingsStore.customSoundUri,
            customSoundName = settingsStore.customSoundName,
            staleTaskThresholdDays = settingsStore.staleTaskThresholdDays
        )
    }

    fun setStaleTaskThresholdDays(days: Int) {
        settingsStore.staleTaskThresholdDays = days
        _uiState.value = _uiState.value.copy(staleTaskThresholdDays = days)
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
