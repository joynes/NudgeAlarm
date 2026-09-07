package se.joynes.nudgealarm.ui

import android.app.Application
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

data class EditRemindersUiState(
    val reminders: List<ReminderEntity> = emptyList(),
    val isLoading: Boolean = true,
    val editingReminder: ReminderEntity? = null,
    val isAddingNew: Boolean = false
)

class EditRemindersViewModel(application: Application) : AndroidViewModel(application) {

    private val reminderRepository: ReminderRepository

    private val _uiState = MutableStateFlow(EditRemindersUiState())
    val uiState: StateFlow<EditRemindersUiState> = _uiState.asStateFlow()

    init {
        val database = NagDatabase.getInstance(application)
        reminderRepository = ReminderRepository(database.reminderDao())
        loadReminders()
    }

    private fun loadReminders() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val reminders = reminderRepository.getAllReminders()
                _uiState.value = _uiState.value.copy(
                    reminders = reminders,
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
        sticky: Boolean
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
                        sticky = sticky
                    )
                }
            }
            _uiState.value = _uiState.value.copy(editingReminder = null, isAddingNew = false)
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

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        loadReminders()
    }
}
