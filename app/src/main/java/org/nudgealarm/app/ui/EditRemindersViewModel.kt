package org.nudgealarm.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nudgealarm.app.database.NagDatabase
import org.nudgealarm.app.database.ReminderEntity
import org.nudgealarm.app.database.ReminderRepository

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

    fun cancelEdit() {
        _uiState.value = _uiState.value.copy(editingReminder = null, isAddingNew = false)
    }

    fun saveReminder(
        title: String,
        schedule: String,
        nagIntervalMinutes: Int,
        maxNags: Int
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
                        updatedAt = System.currentTimeMillis()
                    )
                    reminderRepository.update(updated)
                } else {
                    // Create new
                    reminderRepository.create(
                        title = title,
                        schedule = schedule,
                        nagIntervalMinutes = nagIntervalMinutes,
                        maxNags = maxNags
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
