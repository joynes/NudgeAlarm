package se.joynes.nudgealarm.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.joynes.nudgealarm.database.NagDatabase
import se.joynes.nudgealarm.database.ReminderEntity
import se.joynes.nudgealarm.database.ReminderRepository
import se.joynes.nudgealarm.database.SavedPlaceRepository
import se.joynes.nudgealarm.database.SavedPlaceWithLocations
import se.joynes.nudgealarm.storage.QuestYamlExporter

data class EditRemindersUiState(
    val reminders: List<ReminderEntity> = emptyList(),
    val isLoading: Boolean = true,
    val editingReminder: ReminderEntity? = null,
    val isAddingNew: Boolean = false,
    val savedPlaces: List<SavedPlaceWithLocations> = emptyList()
)

class EditRemindersViewModel(application: Application) : AndroidViewModel(application) {

    private val reminderRepository: ReminderRepository
    private val savedPlaceRepository: SavedPlaceRepository

    private val _uiState = MutableStateFlow(EditRemindersUiState())
    val uiState: StateFlow<EditRemindersUiState> = _uiState.asStateFlow()

    init {
        val database = NagDatabase.getInstance(application)
        reminderRepository = ReminderRepository(database.reminderDao())
        savedPlaceRepository = SavedPlaceRepository(database.savedPlaceDao())
        loadReminders()
    }

    private fun loadReminders() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val reminders = reminderRepository.getAllReminders()
                val savedPlaces = savedPlaceRepository.getAll()
                _uiState.value = _uiState.value.copy(
                    reminders = reminders,
                    savedPlaces = savedPlaces,
                    isLoading = false
                )
            }
        }
    }

    fun startAddNew() {
        _uiState.value = _uiState.value.copy(isAddingNew = true, editingReminder = null)
    }

    fun startEdit(reminder: ReminderEntity) {
        _uiState.value = _uiState.value.copy(editingReminder = reminder, isAddingNew = false)
    }

    fun startEditById(id: String) {
        viewModelScope.launch {
            val reminder = withContext(Dispatchers.IO) {
                reminderRepository.getById(id)
            }
            if (reminder != null) {
                _uiState.value = _uiState.value.copy(
                    editingReminder = reminder,
                    isAddingNew = false
                )
            }
        }
    }

    fun cancelEdit() {
        _uiState.value = _uiState.value.copy(editingReminder = null, isAddingNew = false)
    }

    fun saveReminder(
        title: String,
        schedule: String,
        nagIntervalMinutes: Int,
        maxNags: Int,
        sticky: Boolean,
        placeId: String?
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val editing = _uiState.value.editingReminder
                if (editing != null) {
                    // Update existing
                    val updated = editing.copy(
                        title = title,
                        schedule = schedule,
                        nagIntervalMinutes = nagIntervalMinutes,
                        maxNags = maxNags,
                        sticky = sticky,
                        placeId = placeId,
                        updatedAt = System.currentTimeMillis()
                    )
                    reminderRepository.update(updated)
                } else {
                    // Create new
                    reminderRepository.create(
                        title = title,
                        schedule = schedule,
                        nagIntervalMinutes = nagIntervalMinutes,
                        maxNags = maxNags,
                        sticky = sticky,
                        placeId = placeId
                    )
                }
            }
            _uiState.value = _uiState.value.copy(editingReminder = null, isAddingNew = false)
            loadReminders()
        }
    }

    fun createSavedPlace(name: String, latitude: Double, longitude: Double, radiusMeters: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                savedPlaceRepository.createPlace(name, latitude, longitude, radiusMeters)
            }
            loadReminders()
        }
    }

    fun addSavedPosition(placeId: String, label: String, latitude: Double, longitude: Double, radiusMeters: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                savedPlaceRepository.addLocation(placeId, latitude, longitude, radiusMeters, label)
            }
            loadReminders()
        }
    }

    fun selectSavedPosition(placeId: String, locationId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                savedPlaceRepository.setActiveLocation(placeId, locationId)
            }
            loadReminders()
        }
    }

    fun deleteReminder(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                reminderRepository.delete(id)
            }
            loadReminders()
        }
    }

    fun toggleEnabled(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                reminderRepository.toggleEnabled(id)
            }
            loadReminders()
        }
    }

    fun moveReminder(id: String, offset: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                reminderRepository.move(id, offset)
            }
            loadReminders()
        }
    }

    fun exportSelected(uri: Uri, selectedIds: Set<String>, name: String, onComplete: (Result<Int>) -> Unit = {}) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val selected = reminderRepository.getAllReminders().filter { it.id in selectedIds }
                    require(selected.isNotEmpty()) { "Select at least one quest" }
                    val yaml = QuestYamlExporter.export(selected, name)
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(yaml.toByteArray())
                    } ?: error("Could not open export file")
                    selected.size
                }
            }
            onComplete(result)
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        loadReminders()
    }
}
