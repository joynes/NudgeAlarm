package se.joynes.nudgealarm.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.joynes.nudgealarm.database.*
import se.joynes.nudgealarm.service.ReminderService

data class ReminderPlacePickerState(
    val ruleId: String,
    val title: String = "Reminder",
    val places: List<SavedPlaceWithLocations> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val needsPermission: Boolean = false
)

class ReminderPlaceViewModel(application: Application) : AndroidViewModel(application) {
    private val database = NagDatabase.getInstance(application)
    private val _state = MutableStateFlow<ReminderPlacePickerState?>(null)
    val state = _state.asStateFlow()

    fun open(ruleId: String) {
        _state.value = ReminderPlacePickerState(ruleId)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val rule = requireNotNull(database.reminderDao().getById(ruleId)) { "This quest no longer exists." }
                ReminderPlacePickerState(ruleId, rule.title,
                    SavedPlaceRepository(database.savedPlaceDao()).getAll(), loading = false,
                    needsPermission = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        .any { ContextCompat.checkSelfPermission(getApplication(), it) != PackageManager.PERMISSION_GRANTED })
            } }
            if (_state.value?.ruleId == ruleId) _state.value = result.getOrElse {
                ReminderPlacePickerState(ruleId, loading = false, error = it.message)
            }
        }
    }

    fun dismiss() { if (_state.value?.saving != true) _state.value = null }

    fun select(placeId: String) {
        val current = _state.value ?: return
        if (current.loading || current.saving) return
        _state.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                database.assignReminderPlace(current.ruleId, placeId)
            } }
            result.onSuccess {
                // Re-evaluate already posted notifications and synchronize geofences immediately.
                if (ReminderService.isRunning) {
                    getApplication<Application>().startService(Intent(getApplication(), ReminderService::class.java)
                        .setAction(ReminderService.ACTION_REFRESH_NOTIFICATION))
                    getApplication<Application>().startService(Intent(getApplication(), ReminderService::class.java)
                        .setAction(ReminderService.ACTION_RELOAD))
                }
                _state.value = null
                android.widget.Toast.makeText(getApplication(), "Place saved — reminders only at this place", android.widget.Toast.LENGTH_LONG).show()
            }.onFailure { _state.value = current.copy(saving = false, error = it.message ?: "Could not save place. Try again.") }
        }
    }
}

@Composable
fun ReminderPlacePicker(state: ReminderPlacePickerState, onSelect: (String) -> Unit,
                        onEdit: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!state.saving) onDismiss() },
        title = { Text("Only at a place") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.title, style = MaterialTheme.typography.titleMedium)
                Text("Choose where this reminder may notify you. Applies now and to future reminders.")
                if (state.loading || state.saving) CircularProgressIndicator()
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!state.loading) {
                    if (state.places.isEmpty()) Text("No saved places yet. Add a place in Edit Quest.")
                    state.places.forEach { place ->
                        OutlinedButton(onClick = { onSelect(place.place.id) },
                            enabled = !state.saving && place.activeLocation != null,
                            modifier = Modifier.fillMaxWidth().testTag("quick-place-${place.place.id}")) {
                            Text(place.place.name + if (place.activeLocation == null) " · Needs a position" else "")
                        }
                    }
                    if (state.needsPermission) Text("Place reminders wait until precise location and ‘Allow all the time’ are enabled. Open Edit Quest to set up access.")
                }
            }
        },
        confirmButton = { TextButton(onClick = onEdit, enabled = !state.saving) { Text("Edit Quest / Add place") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.saving) { Text("Cancel") } }
    )
}
