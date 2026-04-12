package org.nudgealarm.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nudgealarm.app.core.config.AppConfig
import org.nudgealarm.app.core.config.ReminderConfig
import org.nudgealarm.app.core.cron.CronExpression
import org.nudgealarm.app.core.event.Event
import org.nudgealarm.app.database.NagDatabase
import org.nudgealarm.app.database.NagRepository
import org.nudgealarm.app.database.ReminderRepository
import org.nudgealarm.app.service.ReminderService
import org.nudgealarm.app.storage.AppStateStore
import org.nudgealarm.app.storage.DataExportStore
import org.nudgealarm.app.storage.EventLogStore
import org.nudgealarm.app.storage.SavedGame
import org.nudgealarm.app.storage.SavedGamesStore
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ActiveReminderUi(
    val ruleId: String,
    val title: String,
    val triggeredAt: String,
    val snoozedUntil: String? = null,
    val snoozeCount: Int = 0
)

data class ScheduledReminderUi(
    val ruleId: String,
    val title: String,
    val scheduledTime: String,
    val scheduledTimeMillis: Long,
    val isSnoozed: Boolean = false,
    val schedule: String = ""
)

data class MainUiState(
    val isServiceRunning: Boolean = false,
    val configFilePath: String? = null,
    val hasGameLoaded: Boolean = false,  // True when a game/preset has been selected this session
    val currentGameName: String? = null,  // Name of the currently loaded game
    val currentGameId: String? = null,    // ID of the currently loaded game
    val nextTriggerRule: String? = null,
    val nextTriggerTime: String? = null,
    val activeRulesCount: Int = 0,
    val totalRulesCount: Int = 0,
    val events: List<Event> = emptyList(),
    val activeReminders: List<ActiveReminderUi> = emptyList(),
    val todaysSchedule: List<ScheduledReminderUi> = emptyList(),
    val savedGames: List<SavedGame> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val eventLogStore = EventLogStore(application)
    private val savedGamesStore = SavedGamesStore(application)
    private val appStateStore = AppStateStore(application)
    private val dataExportStore = DataExportStore(application)
    private val reminderRepository: ReminderRepository
    private val nagRepository: NagRepository

    // Track if a game has been loaded this session
    private var gameLoaded = false
    private var gameName: String? = null
    private var gameId: String? = null

    init {
        val database = NagDatabase.getInstance(application)
        reminderRepository = ReminderRepository(database.reminderDao())
        nagRepository = NagRepository(database.nagStateDao())

        // Restore game state from persistent storage
        viewModelScope.launch(Dispatchers.IO) {
            val savedGameId = appStateStore.getCurrentGameId()
            val savedGameName = appStateStore.getCurrentGameName()

            if (ReminderService.isRunning) {
                // Service is running, use its state
                gameLoaded = true
                gameId = savedGameId
                gameName = savedGameName ?: "Active Game"
                eventLogStore.add(Event.Debug(detail = "Service running, restored game: $gameName (id=$gameId)"))
            } else if (savedGameId != null) {
                // Service not running but we have a saved game - restore it
                val hasReminders = reminderRepository.hasReminders()
                if (hasReminders) {
                    // Database has reminders, restore the game state
                    gameLoaded = true
                    gameId = savedGameId
                    gameName = savedGameName ?: "Restored Game"
                    eventLogStore.add(Event.Debug(detail = "Restored game from storage: $gameName (id=$gameId)"))

                    // Auto-restart the service if it was running before
                    if (appStateStore.wasServiceRunning()) {
                        withContext(Dispatchers.Main) {
                            eventLogStore.add(Event.Debug(detail = "Auto-restarting service (was running before)"))
                            startService()
                        }
                    }
                } else {
                    eventLogStore.add(Event.Debug(detail = "Saved game found but no reminders in database"))
                }
            } else {
                eventLogStore.add(Event.Debug(detail = "No saved game state to restore"))
            }
        }
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var configUri: Uri? = null

    fun startPolling() {
        eventLogStore.add(Event.Debug(detail = "ViewModel startPolling called"))
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                updateState()
                delay(2000)
            }
        }
    }

    private suspend fun updateState() {
        val events = withContext(Dispatchers.IO) {
            eventLogStore.getAll()
        }
        val dbQuestCount = withContext(Dispatchers.IO) {
            reminderRepository.getAllReminders().size
        }
        val nextTime = ReminderService.nextTriggerTime
        val nextTimeStr = nextTime?.let {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
        }

        // Separate active reminders into truly active vs snoozed
        val now = System.currentTimeMillis()
        val (snoozedReminders, trulyActiveReminders) = ReminderService.activeReminders.values.partition {
            it.snoozedUntil != null && it.snoozedUntil > now
        }

        // Only show truly active reminders in Active Quests (not snoozed ones)
        val activeRemindersUi = trulyActiveReminders.map { active ->
            ActiveReminderUi(
                ruleId = active.ruleId,
                title = active.title,
                triggeredAt = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(active.triggeredAt)),
                snoozedUntil = null,
                snoozeCount = active.snoozeCount
            )
        }

        // Compute today's remaining schedule
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }.timeInMillis

        // Get completed/cancelled rule IDs from database to filter them out
        val completedRuleIds = withContext(Dispatchers.IO) {
            nagRepository.getCompletedOrCancelledRuleIdsToday()
        }

        // Get truly active reminder IDs (not snoozed) to exclude from schedule
        val trulyActiveRuleIds = trulyActiveReminders.map { it.ruleId }.toSet()

        // Build schedule from regular reminders
        val regularSchedule = ReminderService.currentConfig?.reminders?.mapNotNull { rule ->
            // Skip if already completed today
            if (rule.id in completedRuleIds) return@mapNotNull null

            // Skip if truly active (showing in ACTIVE QUESTS section)
            if (rule.id in trulyActiveRuleIds) return@mapNotNull null

            // Skip if snoozed (will be added separately with snooze time)
            if (snoozedReminders.any { it.ruleId == rule.id }) return@mapNotNull null

            try {
                if (rule.schedule.startsWith("once:")) {
                    // One-time event: check if scheduled for today
                    val timestamp = rule.schedule.removePrefix("once:").toLongOrNull() ?: return@mapNotNull null
                    if (timestamp in startOfDay..endOfDay) {
                        ScheduledReminderUi(
                            ruleId = rule.id,
                            title = rule.title,
                            scheduledTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp)),
                            scheduledTimeMillis = timestamp,
                            schedule = rule.schedule
                        )
                    } else null
                } else {
                    val cron = CronExpression.parse(rule.schedule)
                    val nextTrigger = cron.nextTriggerTime(startOfDay) // Check from start of day
                    // Show if scheduled for today (even if time has passed - notification may be delayed)
                    if (nextTrigger in startOfDay..endOfDay) {
                        ScheduledReminderUi(
                            ruleId = rule.id,
                            title = rule.title,
                            scheduledTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nextTrigger)),
                            scheduledTimeMillis = nextTrigger,
                            schedule = rule.schedule
                        )
                    } else null
                }
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()

        // Add snoozed reminders to schedule with their snooze time
        val snoozedSchedule = snoozedReminders.mapNotNull { snoozed ->
            val snoozedUntil = snoozed.snoozedUntil ?: return@mapNotNull null
            // Only show if snooze time is today
            if (snoozedUntil in startOfDay..endOfDay) {
                val rule = ReminderService.currentConfig?.reminders?.find { it.id == snoozed.ruleId }
                ScheduledReminderUi(
                    ruleId = snoozed.ruleId,
                    title = "${snoozed.title} (snoozed)",
                    scheduledTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(snoozedUntil)),
                    scheduledTimeMillis = snoozedUntil,
                    isSnoozed = true,
                    schedule = rule?.schedule ?: ""
                )
            } else null
        }

        // Combine and sort by time
        val todaysSchedule = (regularSchedule + snoozedSchedule).sortedBy { it.scheduledTimeMillis }

        val savedGames = withContext(Dispatchers.IO) {
            savedGamesStore.getAll()
        }

        _uiState.value = MainUiState(
            isServiceRunning = ReminderService.isRunning,
            configFilePath = configUri?.path ?: configUri?.toString(),
            hasGameLoaded = gameLoaded || ReminderService.isRunning,
            currentGameName = gameName ?: if (ReminderService.isRunning) "Active Game" else null,
            currentGameId = gameId,
            nextTriggerRule = ReminderService.nextTriggerRuleId,
            nextTriggerTime = nextTimeStr,
            activeRulesCount = ReminderService.currentConfig?.reminders?.size ?: 0,
            totalRulesCount = if (ReminderService.isRunning) {
                ReminderService.currentConfig?.reminders?.size ?: 0
            } else if (gameLoaded) {
                dbQuestCount
            } else {
                0
            },
            events = events.reversed(),
            activeReminders = activeRemindersUi,
            todaysSchedule = todaysSchedule,
            savedGames = savedGames
        )
    }

    fun startService() {
        viewModelScope.launch(Dispatchers.IO) {
            // Always export from database to ensure quests are up-to-date
            val uri = exportDatabaseToYaml()
            if (uri == null) {
                // No quests in database, try existing configUri as fallback
                val fallbackUri = configUri
                if (fallbackUri == null) {
                    eventLogStore.add(Event.UiAction(action = "Start Service pressed but no quests available"))
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    eventLogStore.add(Event.UiAction(action = "Start Service with preset config"))
                    val intent = Intent(getApplication(), ReminderService::class.java).apply {
                        action = ReminderService.ACTION_START
                        putExtra(ReminderService.EXTRA_CONFIG_URI, fallbackUri.toString())
                    }
                    getApplication<Application>().startForegroundService(intent)
                    appStateStore.setServiceWasRunning(true)
                }
            } else {
                configUri = uri
                withContext(Dispatchers.Main) {
                    eventLogStore.add(Event.UiAction(action = "Start Service pressed, config=$uri"))
                    val intent = Intent(getApplication(), ReminderService::class.java).apply {
                        action = ReminderService.ACTION_START
                        putExtra(ReminderService.EXTRA_CONFIG_URI, uri.toString())
                    }
                    getApplication<Application>().startForegroundService(intent)
                    appStateStore.setServiceWasRunning(true)
                }
            }
            updateState()
        }
    }

    fun stopService() {
        eventLogStore.add(Event.UiAction(action = "Stop Service pressed"))
        appStateStore.setServiceWasRunning(false)
        val intent = Intent(getApplication(), ReminderService::class.java).apply {
            action = ReminderService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
    }

    fun reloadConfig() {
        eventLogStore.add(Event.UiAction(action = "Reload Config pressed"))
        val intent = Intent(getApplication(), ReminderService::class.java).apply {
            action = ReminderService.ACTION_RELOAD
        }
        getApplication<Application>().startService(intent)
    }

    /**
     * Save current game schema to a file for backup/sharing.
     */
    fun saveToFile(uri: Uri) {
        eventLogStore.add(Event.UiAction(action = "Save to file: $uri"))
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val configs = reminderRepository.getEnabledAsConfigs()
                if (configs.isEmpty()) {
                    eventLogStore.add(Event.ConfigError(error = "No quests to save"))
                    return@launch
                }

                val yaml = buildString {
                    appendLine("# NudgeAlarm Game Save")
                    appendLine("# Name: ${gameName ?: "Unnamed Game"}")
                    appendLine("# Quests: ${configs.size}")
                    appendLine()
                    appendLine("reminders:")
                    configs.forEach { config ->
                        appendLine("  - id: ${config.id}")
                        appendLine("    title: \"${config.title}\"")
                        appendLine("    schedule: \"${config.schedule}\"")
                        appendLine("    nag_interval: ${config.nagInterval.inWholeMinutes}m")
                        appendLine("    max_nags: ${config.maxNags}")
                        appendLine("    sound: ${config.sound}")
                        appendLine("    vibration: ${config.vibration}")
                        appendLine("    snooze_options:")
                        config.snoozeOptions.forEach { snooze ->
                            val value = if (snooze.inWholeHours >= 1) "${snooze.inWholeHours}h" else "${snooze.inWholeMinutes}m"
                            appendLine("      - $value")
                        }
                        appendLine()
                    }
                }

                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(yaml.toByteArray())
                }

                eventLogStore.add(Event.Debug(detail = "Saved ${configs.size} quests to file"))
            } catch (e: Exception) {
                eventLogStore.add(Event.ConfigError(error = "Failed to save: ${e.message}"))
            }
        }
    }

    /**
     * Export current game to YAML string for clipboard.
     * Returns the YAML content or null if no quests.
     */
    suspend fun exportToClipboard(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val configs = reminderRepository.getEnabledAsConfigs()
                if (configs.isEmpty()) {
                    eventLogStore.add(Event.ConfigError(error = "No quests to export"))
                    return@withContext null
                }

                val yaml = buildString {
                    appendLine("# NudgeAlarm Game Export")
                    appendLine("# Paste this into ChatGPT/Claude to edit, then copy back and import!")
                    appendLine("# Name: ${gameName ?: "Unnamed Game"}")
                    appendLine("# Quests: ${configs.size}")
                    appendLine()
                    appendLine("reminders:")
                    configs.forEach { config ->
                        appendLine("  - id: ${config.id}")
                        appendLine("    title: \"${config.title}\"")
                        appendLine("    schedule: \"${config.schedule}\"")
                        appendLine("    nag_interval: ${config.nagInterval.inWholeMinutes}m")
                        appendLine("    max_nags: ${config.maxNags}")
                        appendLine()
                    }
                }

                eventLogStore.add(Event.Debug(detail = "Exported ${configs.size} quests to clipboard"))
                yaml
            } catch (e: Exception) {
                eventLogStore.add(Event.ConfigError(error = "Export failed: ${e.message}"))
                null
            }
        }
    }

    /**
     * Import game from YAML string (from clipboard).
     * Returns true on success, false on failure.
     */
    suspend fun importFromClipboard(yaml: String): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                eventLogStore.add(Event.UiAction(action = "Import from clipboard"))

                // Parse YAML using ConfigLoader
                val configLoader = org.nudgealarm.app.storage.ConfigLoader(getApplication())
                val result = configLoader.loadFromString(yaml)

                result.fold(
                    onSuccess = { appConfig ->
                        if (appConfig.reminders.isEmpty()) {
                            eventLogStore.add(Event.ConfigError(error = "No quests found in clipboard"))
                            return@withContext Result.failure(Exception("No quests found in clipboard"))
                        }

                        // Clear existing and import new
                        reminderRepository.clearAll()
                        reminderRepository.importFromConfigs(appConfig.reminders)

                        // Update game state
                        val questCount = appConfig.reminders.size
                        gameLoaded = true

                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(
                                hasGameLoaded = true,
                                totalRulesCount = questCount
                            )
                            // Restart service to pick up changes
                            restartService()
                        }

                        eventLogStore.add(Event.ConfigLoaded(ruleCount = questCount))
                        Result.success(questCount)
                    },
                    onFailure = { error ->
                        eventLogStore.add(Event.ConfigError(error = "Import failed: ${error.message}"))
                        Result.failure(error)
                    }
                )
            } catch (e: Exception) {
                eventLogStore.add(Event.ConfigError(error = "Import failed: ${e.message}"))
                Result.failure(e)
            }
        }
    }

    /**
     * Load a game schema from a YAML file.
     */
    fun loadFromFile(uri: Uri) {
        eventLogStore.add(Event.UiAction(action = "Load from file: $uri"))
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Take persistable permission for future access
                try {
                    getApplication<Application>().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Permission might not be persistable, continue anyway
                }

                // Load config using ConfigLoader
                val configLoader = org.nudgealarm.app.storage.ConfigLoader(getApplication())
                val result = configLoader.loadFromUri(uri)

                result.fold(
                    onSuccess = { appConfig ->
                        // Extract game name from file name via ContentResolver
                        val displayName = getApplication<Application>().contentResolver.query(
                            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                        val extractedName = (displayName ?: uri.lastPathSegment ?: "Loaded Game")
                            .removeSuffix(".yaml")
                            .removeSuffix(".yml")
                            .replace("_", " ")
                            .replace(Regex("^.*[/:]"), "") // Remove path prefix

                        // Generate unique ID for this game
                        val newGameId = "file_${System.currentTimeMillis()}"

                        // Clear existing and import new
                        reminderRepository.clearAll()
                        reminderRepository.importFromConfigs(appConfig.reminders)

                        configUri = uri
                        gameLoaded = true
                        gameName = extractedName
                        gameId = newGameId
                        appStateStore.setCurrentGame(newGameId, extractedName)

                        // Save to games list
                        savedGamesStore.save(SavedGame(
                            id = newGameId,
                            name = extractedName,
                            questCount = appConfig.reminders.size,
                            isPreset = false,
                            filePath = uri.toString()
                        ))

                        eventLogStore.add(Event.ConfigLoaded(ruleCount = appConfig.reminders.size))
                        eventLogStore.add(Event.TestConfigCreated(configName = "$extractedName (${appConfig.reminders.size} quests)"))

                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(
                                configFilePath = extractedName,
                                hasGameLoaded = true,
                                currentGameName = extractedName,
                                currentGameId = newGameId,
                                totalRulesCount = appConfig.reminders.size
                            )
                            // Auto-start service after loading (restart if already running)
                            restartService()
                        }
                    },
                    onFailure = { error ->
                        eventLogStore.add(Event.ConfigError(error = "Failed to load: ${error.message}"))
                    }
                )
            } catch (e: Exception) {
                eventLogStore.add(Event.ConfigError(error = "Failed to load file: ${e.message}"))
            }
        }
    }

    fun markReminderDone(ruleId: String) {
        eventLogStore.add(Event.UiAction(action = "Mark done from UI: $ruleId"))
        val intent = Intent(getApplication(), ReminderService::class.java).apply {
            action = ReminderService.ACTION_DONE_FROM_UI
            putExtra(ReminderService.EXTRA_RULE_ID, ruleId)
        }
        getApplication<Application>().startService(intent)
        // Trigger immediate UI update
        viewModelScope.launch {
            delay(100) // Small delay for service to process
            updateState()
        }
    }

    fun markAllRemindersDone(ruleIds: List<String>) {
        eventLogStore.add(Event.UiAction(action = "Mark all done from UI: ${ruleIds.joinToString()}"))
        val intent = Intent(getApplication(), ReminderService::class.java).apply {
            action = ReminderService.ACTION_DONE_ALL_FROM_UI
            putExtra(ReminderService.EXTRA_RULE_IDS, ruleIds.toTypedArray())
        }
        getApplication<Application>().startService(intent)
        viewModelScope.launch {
            delay(200)
            updateState()
        }
    }

    fun cancelAllReminders(ruleIds: List<String>) {
        eventLogStore.add(Event.UiAction(action = "Cancel all from UI: ${ruleIds.joinToString()}"))
        val intent = Intent(getApplication(), ReminderService::class.java).apply {
            action = ReminderService.ACTION_CANCEL_ALL_FROM_UI
            putExtra(ReminderService.EXTRA_RULE_IDS, ruleIds.toTypedArray())
        }
        getApplication<Application>().startService(intent)
        viewModelScope.launch {
            delay(200)
            updateState()
        }
    }

    fun snoozeReminder(ruleId: String, minutes: Int) {
        eventLogStore.add(Event.UiAction(action = "Snooze from UI: $ruleId for ${minutes}m"))
        val intent = Intent(getApplication(), ReminderService::class.java).apply {
            action = ReminderService.ACTION_SNOOZE_FROM_UI
            putExtra(ReminderService.EXTRA_RULE_ID, ruleId)
            putExtra(ReminderService.EXTRA_SNOOZE_MINUTES, minutes)
        }
        getApplication<Application>().startService(intent)
        // Trigger immediate UI update
        viewModelScope.launch {
            delay(100) // Small delay for service to process
            updateState()
        }
    }

    fun cancelReminder(ruleId: String) {
        eventLogStore.add(Event.UiAction(action = "Cancel from UI: $ruleId"))
        val intent = Intent(getApplication(), ReminderService::class.java).apply {
            action = ReminderService.ACTION_CANCEL_FROM_UI
            putExtra(ReminderService.EXTRA_RULE_ID, ruleId)
        }
        getApplication<Application>().startService(intent)
        // Trigger immediate UI update
        viewModelScope.launch {
            delay(100) // Small delay for service to process
            updateState()
        }
    }

    /**
     * Create a new empty schema - clears database so user can add their own quests.
     * Returns immediately after database is cleared (suspending function).
     */
    fun createNewSchema(name: String = "New Game", onComplete: () -> Unit = {}) {
        eventLogStore.add(Event.UiAction(action = "Create new schema: $name"))
        viewModelScope.launch(Dispatchers.IO) {
            reminderRepository.clearAll()
            configUri = null

            // Generate unique ID for this game
            val newGameId = "custom_${System.currentTimeMillis()}"
            gameLoaded = true
            gameName = name
            gameId = newGameId
            appStateStore.setCurrentGame(newGameId, name)

            // Save to games list (will be updated with quest count later)
            savedGamesStore.save(SavedGame(
                id = newGameId,
                name = name,
                questCount = 0,
                isPreset = false
            ))

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    configFilePath = null,
                    hasGameLoaded = true,
                    currentGameName = name,
                    currentGameId = newGameId,
                    totalRulesCount = 0
                )
                eventLogStore.add(Event.TestConfigCreated(configName = "New schema '$name' created"))
                onComplete()
            }
        }
    }

    /**
     * Load a saved game from the list.
     */
    fun loadSavedGame(savedGame: SavedGame) {
        eventLogStore.add(Event.UiAction(action = "Load saved game: ${savedGame.name}"))

        // If it's a preset, use loadPreset
        if (savedGame.isPreset) {
            loadPreset(savedGame.id)
            return
        }

        // If it has a file path, load from file
        if (savedGame.filePath != null) {
            loadFromFile(Uri.parse(savedGame.filePath))
            return
        }

        // For custom games, try to load from internal storage
        viewModelScope.launch(Dispatchers.IO) {
            val internalFile = File(getApplication<Application>().filesDir, "game_${savedGame.id}.yaml")
            if (internalFile.exists()) {
                val configLoader = org.nudgealarm.app.storage.ConfigLoader(getApplication())
                val result = configLoader.loadFromUri(Uri.fromFile(internalFile))

                result.fold(
                    onSuccess = { appConfig ->
                        reminderRepository.clearAll()
                        reminderRepository.importFromConfigs(appConfig.reminders)

                        configUri = Uri.fromFile(internalFile)
                        gameLoaded = true
                        gameName = savedGame.name
                        gameId = savedGame.id
                        appStateStore.setCurrentGame(savedGame.id, savedGame.name)

                        eventLogStore.add(Event.ConfigLoaded(ruleCount = appConfig.reminders.size))

                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(
                                hasGameLoaded = true,
                                currentGameName = savedGame.name,
                                currentGameId = savedGame.id,
                                totalRulesCount = appConfig.reminders.size
                            )
                            // Auto-start service after loading (restart if already running)
                            restartService()
                        }
                    },
                    onFailure = { error ->
                        eventLogStore.add(Event.ConfigError(error = "Failed to load game: ${error.message}"))
                    }
                )
            } else {
                // No saved data, just set the game as current with empty quests
                reminderRepository.clearAll()
                gameLoaded = true
                gameName = savedGame.name
                gameId = savedGame.id
                appStateStore.setCurrentGame(savedGame.id, savedGame.name)

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        hasGameLoaded = true,
                        currentGameName = savedGame.name,
                        currentGameId = savedGame.id,
                        totalRulesCount = 0
                    )
                }
            }
        }
    }

    /**
     * Save current game's quests to internal storage.
     * Call this when leaving Edit Quests or before switching games.
     */
    fun saveCurrentGame() {
        val currentId = gameId ?: return
        val currentName = gameName ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val configs = reminderRepository.getEnabledAsConfigs()
            val allConfigs = reminderRepository.getAllReminders().map { it.toReminderConfig() }

            // Save to internal file
            val yaml = buildString {
                appendLine("reminders:")
                allConfigs.forEach { config ->
                    appendLine("  - id: ${config.id}")
                    appendLine("    title: \"${config.title}\"")
                    appendLine("    schedule: \"${config.schedule}\"")
                    appendLine("    nag_interval: ${config.nagInterval.inWholeMinutes}m")
                    appendLine("    max_nags: ${config.maxNags}")
                }
            }

            val internalFile = File(getApplication<Application>().filesDir, "game_${currentId}.yaml")
            internalFile.writeText(yaml)

            // Update saved game with quest count
            val existingGame = savedGamesStore.get(currentId)
            if (existingGame != null) {
                savedGamesStore.save(existingGame.copy(questCount = allConfigs.size))
            } else {
                // Create new entry if it doesn't exist
                savedGamesStore.save(SavedGame(
                    id = currentId,
                    name = currentName,
                    questCount = allConfigs.size,
                    isPreset = false
                ))
            }

            eventLogStore.add(Event.Debug(detail = "Saved game '$currentName' with ${allConfigs.size} quests"))

            // Reload service config so it picks up new/changed quests
            if (ReminderService.isRunning) {
                withContext(Dispatchers.Main) {
                    reloadConfig()
                }
            }
        }
    }

    /**
     * Delete a saved game from the list.
     */
    fun deleteSavedGame(gameId: String) {
        eventLogStore.add(Event.UiAction(action = "Delete saved game: $gameId"))
        viewModelScope.launch(Dispatchers.IO) {
            savedGamesStore.delete(gameId)
            updateState()
        }
    }

    /**
     * Update the quest count for the current game.
     */
    fun updateCurrentGameQuestCount() {
        val currentId = gameId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val questCount = reminderRepository.getAllReminders().size
            val existingGame = savedGamesStore.get(currentId)
            if (existingGame != null) {
                savedGamesStore.save(existingGame.copy(questCount = questCount))
            }
        }
    }

    /**
     * Rename the current game.
     */
    fun renameCurrentGame(newName: String) {
        val currentId = gameId ?: return
        val trimmedName = newName.trim()
        if (trimmedName.isEmpty()) return

        eventLogStore.add(Event.UiAction(action = "Rename game to: $trimmedName"))

        viewModelScope.launch(Dispatchers.IO) {
            gameName = trimmedName
            appStateStore.setCurrentGame(currentId, trimmedName)

            // Update in saved games list
            val existingGame = savedGamesStore.get(currentId)
            if (existingGame != null) {
                savedGamesStore.save(existingGame.copy(name = trimmedName))
            }

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    currentGameName = trimmedName
                )
            }
        }
    }

    /**
     * Export database reminders to YAML file for service.
     */
    private suspend fun exportDatabaseToYaml(): Uri? {
        val configs = reminderRepository.getEnabledAsConfigs()
        if (configs.isEmpty()) {
            eventLogStore.add(Event.ConfigError(error = "No quests to export - add quests first"))
            return null
        }

        val yaml = buildString {
            appendLine("reminders:")
            configs.forEach { config ->
                appendLine("  - id: ${config.id}")
                appendLine("    title: \"${config.title}\"")
                appendLine("    schedule: \"${config.schedule}\"")
                appendLine("    nag_interval: ${config.nagInterval.inWholeMinutes}m")
                appendLine("    max_nags: ${config.maxNags}")
                appendLine("    sound: ${config.sound}")
                appendLine("    vibration: ${config.vibration}")
                appendLine("    snooze_options:")
                config.snoozeOptions.forEach { snooze ->
                    val value = if (snooze.inWholeHours >= 1) "${snooze.inWholeHours}h" else "${snooze.inWholeMinutes}m"
                    appendLine("      - $value")
                }
                appendLine()
            }
        }

        val file = File(getApplication<Application>().filesDir, "custom_config.yaml")
        file.writeText(yaml)
        eventLogStore.add(Event.Debug(detail = "Exported ${configs.size} quests to YAML"))
        return Uri.fromFile(file)
    }

    /**
     * Start service - exports database to YAML first if no config is set.
     */
    fun startServiceFromDatabase() {
        eventLogStore.add(Event.UiAction(action = "Start Service from database"))
        viewModelScope.launch(Dispatchers.IO) {
            val uri = exportDatabaseToYaml()
            if (uri != null) {
                configUri = uri
                withContext(Dispatchers.Main) {
                    val intent = Intent(getApplication(), ReminderService::class.java).apply {
                        action = ReminderService.ACTION_START
                        putExtra(ReminderService.EXTRA_CONFIG_URI, uri.toString())
                    }
                    getApplication<Application>().startForegroundService(intent)
                }
                updateState()
            }
        }
    }

    /**
     * Export all database data + settings to a JSON file at the given URI.
     */
    fun exportAllData(uri: Uri, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = dataExportStore.exportToUri(uri)
            result.fold(
                onSuccess = { count -> eventLogStore.add(Event.Debug(detail = "Exported $count quests to file")) },
                onFailure = { e -> eventLogStore.add(Event.ConfigError(error = "Export failed: ${e.message}")) }
            )
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    /**
     * Import all database data + settings from a JSON file at the given URI.
     */
    fun importAllData(uri: Uri, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = dataExportStore.importFromUri(uri)
            result.fold(
                onSuccess = { count ->
                    eventLogStore.add(Event.Debug(detail = "Imported $count quests from file"))
                    gameLoaded = count > 0
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(hasGameLoaded = count > 0, totalRulesCount = count)
                        if (count > 0) restartService()
                        onResult(result)
                    }
                },
                onFailure = { e ->
                    eventLogStore.add(Event.ConfigError(error = "Import failed: ${e.message}"))
                    withContext(Dispatchers.Main) { onResult(result) }
                }
            )
        }
    }

    fun clearEventLog() {
        viewModelScope.launch(Dispatchers.IO) {
            eventLogStore.clear()
            eventLogStore.add(Event.UiAction(action = "Event log cleared"))
            updateState()
        }
    }

    /**
     * Restart the service with the current config.
     * Stops the service if running, then starts it again.
     */
    private fun restartService() {
        if (ReminderService.isRunning) {
            eventLogStore.add(Event.Debug(detail = "Restarting service with new config"))
            stopService()
            // Small delay to ensure service is fully stopped before restarting
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                startService()
            }, 500)
        } else {
            startService()
        }
    }

    fun loadPreset(presetId: String) {
        eventLogStore.add(Event.UiAction(action = "Load preset: $presetId"))
        viewModelScope.launch(Dispatchers.IO) {
            val (yaml, displayName, configs) = when (presetId) {
                "test_1min" -> createTest1MinConfig()
                "test_hourly" -> createTestHourlyConfig()
                "fitness_focus" -> createFitnessFocusConfig()
                "student_life" -> createStudentLifeConfig()
                "self_care" -> createSelfCareConfig()
                "productivity" -> createProductivityConfig()
                "parent_life" -> createParentLifeConfig()
                "healthy_habits" -> createHealthyHabitsConfig()
                else -> {
                    eventLogStore.add(Event.ConfigError(error = "Unknown preset: $presetId"))
                    return@launch
                }
            }

            // Save YAML file for service
            val file = File(getApplication<Application>().filesDir, "$presetId.yaml")
            file.writeText(yaml)

            // Import to database for editing
            reminderRepository.clearAll()
            reminderRepository.importFromConfigs(configs)

            configUri = Uri.fromFile(file)
            gameLoaded = true
            gameName = displayName
            gameId = presetId
            appStateStore.setCurrentGame(presetId, displayName)

            // Save to games list
            savedGamesStore.save(SavedGame(
                id = presetId,
                name = displayName,
                questCount = configs.size,
                isPreset = true
            ))

            eventLogStore.add(Event.TestConfigCreated(configName = "$presetId (${configs.size} quests)"))
            _uiState.value = _uiState.value.copy(
                configFilePath = displayName,
                hasGameLoaded = true,
                currentGameName = displayName,
                currentGameId = presetId
            )

            // Auto-start service after loading preset (restart if already running)
            withContext(Dispatchers.Main) {
                restartService()
            }
        }
    }

    private fun createTest1MinConfig(): Triple<String, String, List<ReminderConfig>> {
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 1) }
        val minute = cal.get(Calendar.MINUTE)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val yaml = """
reminders:
  - id: test_1min
    title: "Test 1min (${String.format("%02d:%02d", hour, minute)})"
    schedule: "$minute $hour * * *"
    nag_interval: 1m
    max_nags: 10
    sound: alarm
    vibration: strong
    snooze_options:
      - 1m
      - 5m
""".trimIndent()
        val configs = listOf(
            ReminderConfig(
                id = "test_1min",
                title = "Test 1min (${String.format("%02d:%02d", hour, minute)})",
                schedule = "$minute $hour * * *",
                nagInterval = 1.minutes,
                maxNags = 10
            )
        )
        return Triple(yaml, "Test 1min (${String.format("%02d:%02d", hour, minute)})", configs)
    }

    private fun createTestHourlyConfig(): Triple<String, String, List<ReminderConfig>> {
        val yaml = """
reminders:
  - id: test_hourly
    title: "Test Hourly (next full hour)"
    schedule: "0 * * * *"
    nag_interval: 1m
    max_nags: 5
    sound: alarm
    vibration: strong
    snooze_options:
      - 1m
      - 5m
""".trimIndent()
        val configs = listOf(
            ReminderConfig(
                id = "test_hourly",
                title = "Test Hourly (next full hour)",
                schedule = "0 * * * *",
                nagInterval = 1.minutes,
                maxNags = 5
            )
        )
        return Triple(yaml, "Test Hourly (every full hour)", configs)
    }

    private fun createFitnessFocusConfig(): Triple<String, String, List<ReminderConfig>> {
        val configs = listOf(
            // Morning
            ReminderConfig("wake_up_fitness", "Wake Up & Hydrate", "30 6 * * 1-5", 5.minutes, 12),
            ReminderConfig("pre_workout_meal", "Pre-workout Meal", "45 6 * * 1-5", 5.minutes, 6),
            ReminderConfig("morning_workout", "Morning Workout", "0 7 * * 1-5", 5.minutes, 12),
            ReminderConfig("post_workout_shake", "Post-workout Shake", "0 8 * * 1-5", 5.minutes, 6),
            ReminderConfig("cold_shower", "Cold Shower", "15 8 * * 1-5", 5.minutes, 6),
            // Daytime
            ReminderConfig("hydration_morning", "Drink Water (500ml)", "0 10 * * *", 5.minutes, 3),
            ReminderConfig("healthy_snack", "Healthy Snack", "30 10 * * 1-5", 5.minutes, 3),
            ReminderConfig("lunch_protein", "High-protein Lunch", "0 12 * * *", 5.minutes, 6),
            ReminderConfig("hydration_afternoon", "Drink Water (500ml)", "0 14 * * *", 5.minutes, 3),
            ReminderConfig("afternoon_protein", "Afternoon Protein", "0 15 * * *", 5.minutes, 6),
            ReminderConfig("creatine", "Take Creatine", "30 15 * * *", 5.minutes, 6),
            // Evening
            ReminderConfig("dinner_prep", "Prep Healthy Dinner", "0 18 * * *", 5.minutes, 6),
            ReminderConfig("evening_walk", "Evening Walk (20 min)", "0 19 * * *", 5.minutes, 6),
            ReminderConfig("stretch_mobility", "Stretch & Mobility", "0 21 * * *", 5.minutes, 6),
            ReminderConfig("sleep_prep", "Sleep Prep (no screens)", "30 21 * * *", 5.minutes, 6),
            ReminderConfig("log_workout", "Log Today's Workout", "0 21 * * 1-5", 5.minutes, 6),
            // Weekend
            ReminderConfig("weekend_run", "Weekend Long Run", "0 8 * * 6", 5.minutes, 12),
            ReminderConfig("weekend_workout", "Weekend Workout", "0 9 * * 0", 5.minutes, 12),
            ReminderConfig("meal_prep", "Meal Prep for the Week", "0 16 * * 0", 5.minutes, 12),
            ReminderConfig("weigh_in", "Weekly Weigh-in", "0 7 * * 1", 5.minutes, 6)
        )
        val yaml = buildPresetsYaml(configs)
        return Triple(yaml, "Fitness Focus (${configs.size} quests)", configs)
    }

    private fun createStudentLifeConfig(): Triple<String, String, List<ReminderConfig>> {
        val configs = listOf(
            // Morning weekdays
            ReminderConfig("wake_up", "Wake Up & Get Ready", "0 7 * * 1-5", 5.minutes, 12),
            ReminderConfig("breakfast", "Eat Breakfast", "20 7 * * 1-5", 5.minutes, 6),
            ReminderConfig("pack_bag", "Pack Bag & Check Schedule", "40 7 * * 1-5", 5.minutes, 6),
            // Study blocks
            ReminderConfig("study_block_1", "Study Block 1", "0 9 * * 1-5", 5.minutes, 6),
            ReminderConfig("short_break_1", "Break - Move Around", "0 10 * * 1-5", 5.minutes, 3),
            ReminderConfig("study_block_2", "Study Block 2", "15 10 * * 1-5", 5.minutes, 6),
            ReminderConfig("lunch_break", "Lunch Break", "0 12 * * 1-5", 5.minutes, 3),
            ReminderConfig("study_block_3", "Study Block 3", "0 13 * * 1-5", 5.minutes, 6),
            ReminderConfig("short_break_2", "Break - Snack & Fresh Air", "0 14 * * 1-5", 5.minutes, 3),
            ReminderConfig("study_block_4", "Study Block 4", "15 14 * * 1-5", 5.minutes, 6),
            // Afternoon/Evening
            ReminderConfig("exercise_break", "Exercise / Gym", "0 16 * * 1-5", 5.minutes, 6),
            ReminderConfig("dinner", "Dinner", "0 18 * * *", 5.minutes, 3),
            ReminderConfig("check_email", "Check School Email", "30 18 * * 1-5", 5.minutes, 6),
            ReminderConfig("homework_review", "Review & Finish Homework", "0 19 * * 0-4", 5.minutes, 6),
            ReminderConfig("tidy_desk", "Tidy Up Desk", "0 20 * * *", 5.minutes, 3),
            ReminderConfig("review_tomorrow", "Prep Tomorrow's Schedule", "0 21 * * 0-4", 5.minutes, 6),
            ReminderConfig("wind_down", "Wind Down - No Screens", "30 22 * * *", 5.minutes, 6),
            ReminderConfig("bedtime", "Bedtime", "0 23 * * 0-4", 5.minutes, 6),
            // Weekend
            ReminderConfig("weekend_wake", "Weekend Wake Up", "0 9 * * 0,6", 5.minutes, 12),
            ReminderConfig("weekly_cleanup", "Weekly Room Cleanup", "0 11 * * 6", 5.minutes, 12),
            ReminderConfig("laundry", "Do Laundry", "0 10 * * 0", 5.minutes, 12),
            ReminderConfig("weekend_study", "Weekend Study Session", "0 14 * * 0,6", 5.minutes, 6)
        )
        val yaml = buildPresetsYaml(configs)
        return Triple(yaml, "Student Life (${configs.size} quests)", configs)
    }

    private fun createSelfCareConfig(): Triple<String, String, List<ReminderConfig>> {
        val configs = listOf(
            // Morning routine
            ReminderConfig("wake_gentle", "Gentle Wake Up", "0 7 * * *", 5.minutes, 12),
            ReminderConfig("morning_water", "Glass of Water", "5 7 * * *", 5.minutes, 3),
            ReminderConfig("morning_stretch", "Morning Stretch (10 min)", "15 7 * * *", 5.minutes, 6),
            ReminderConfig("morning_skincare", "Morning Skincare", "30 7 * * *", 5.minutes, 6),
            ReminderConfig("sunscreen", "Apply Sunscreen", "45 7 * * *", 5.minutes, 6),
            ReminderConfig("vitamins", "Take Vitamins & Supplements", "0 8 * * *", 5.minutes, 6),
            ReminderConfig("healthy_breakfast", "Eat a Healthy Breakfast", "15 8 * * *", 5.minutes, 6),
            // Daytime
            ReminderConfig("posture_check_1", "Posture Check", "0 10 * * *", 5.minutes, 3),
            ReminderConfig("hydration_mid", "Drink Water", "0 11 * * *", 5.minutes, 3),
            ReminderConfig("mindful_lunch", "Mindful Lunch (no screen)", "0 12 * * *", 5.minutes, 6),
            ReminderConfig("mindfulness", "Meditation (10 min)", "0 13 * * *", 5.minutes, 6),
            ReminderConfig("posture_check_2", "Posture Check", "0 15 * * *", 5.minutes, 3),
            ReminderConfig("walk_outside", "Walk Outside (20 min)", "30 15 * * *", 5.minutes, 6),
            ReminderConfig("hydration_pm", "Drink Water", "0 16 * * *", 5.minutes, 3),
            // Evening
            ReminderConfig("cook_dinner", "Cook Something Nourishing", "0 18 * * *", 5.minutes, 6),
            ReminderConfig("gratitude", "3 Things You're Grateful For", "0 20 * * *", 5.minutes, 6),
            ReminderConfig("journal", "Journal / Reflect", "15 20 * * *", 5.minutes, 6),
            ReminderConfig("evening_skincare", "Evening Skincare", "0 21 * * *", 5.minutes, 6),
            ReminderConfig("lip_balm", "Lip Balm & Hand Cream", "15 21 * * *", 5.minutes, 3),
            ReminderConfig("herbal_tea", "Herbal Tea / Wind Down", "30 21 * * *", 5.minutes, 3),
            ReminderConfig("screen_off", "Screens Off", "0 22 * * *", 5.minutes, 6),
            // Weekly
            ReminderConfig("face_mask", "Face Mask", "0 19 * * 3", 5.minutes, 6),
            ReminderConfig("clean_brushes", "Clean Makeup Brushes", "0 11 * * 6", 5.minutes, 6),
            ReminderConfig("nail_care", "Nail Care", "0 14 * * 0", 5.minutes, 6)
        )
        val yaml = buildPresetsYaml(configs)
        return Triple(yaml, "Self Care (${configs.size} quests)", configs)
    }

    private fun createProductivityConfig(): Triple<String, String, List<ReminderConfig>> {
        val configs = listOf(
            // Morning
            ReminderConfig("wake_early", "Wake Up Early", "0 6 * * 1-5", 5.minutes, 12),
            ReminderConfig("morning_routine", "Morning Routine", "15 6 * * 1-5", 5.minutes, 6),
            ReminderConfig("plan_day", "Plan Today's Top 3 Tasks", "0 7 * * 1-5", 5.minutes, 6),
            ReminderConfig("no_social_media", "No Social Media Until Noon", "30 7 * * 1-5", 5.minutes, 3),
            // Work blocks
            ReminderConfig("deep_work_1", "Deep Work Block 1", "0 8 * * 1-5", 5.minutes, 6),
            ReminderConfig("break_1", "Break - Walk & Hydrate", "0 10 * * 1-5", 5.minutes, 3),
            ReminderConfig("deep_work_2", "Deep Work Block 2", "15 10 * * 1-5", 5.minutes, 6),
            ReminderConfig("inbox_check", "Process Inbox (batch)", "0 11 * * 1-5", 5.minutes, 3),
            ReminderConfig("lunch", "Lunch & Recharge", "0 12 * * 1-5", 5.minutes, 3),
            ReminderConfig("power_walk", "Post-lunch Walk (10 min)", "30 12 * * 1-5", 5.minutes, 3),
            ReminderConfig("deep_work_3", "Deep Work Block 3", "0 13 * * 1-5", 5.minutes, 6),
            ReminderConfig("break_2", "Break - Stretch", "0 15 * * 1-5", 5.minutes, 3),
            ReminderConfig("meetings_collab", "Meetings & Collaboration", "15 15 * * 1-5", 5.minutes, 6),
            ReminderConfig("admin_tasks", "Admin & Follow-ups", "0 16 * * 1-5", 5.minutes, 6),
            // End of day
            ReminderConfig("day_review", "Review & Log Progress", "0 17 * * 1-5", 5.minutes, 6),
            ReminderConfig("tomorrow_prep", "Prep Tomorrow's Tasks", "15 17 * * 1-5", 5.minutes, 6),
            ReminderConfig("shutdown_ritual", "Work Shutdown Ritual", "30 17 * * 1-5", 5.minutes, 3),
            // Weekly
            ReminderConfig("weekly_review", "Weekly Review & Planning", "0 16 * * 5", 5.minutes, 12),
            ReminderConfig("clean_workspace", "Clean & Organize Workspace", "0 10 * * 1", 5.minutes, 6),
            ReminderConfig("learn_something", "Learn Something New (30 min)", "0 19 * * 2,4", 5.minutes, 6)
        )
        val yaml = buildPresetsYaml(configs)
        return Triple(yaml, "Productivity (${configs.size} quests)", configs)
    }

    private fun createParentLifeConfig(): Triple<String, String, List<ReminderConfig>> {
        val configs = listOf(
            // Morning
            ReminderConfig("wake_up_parent", "Wake Up Before Kids", "0 6 * * 1-5", 5.minutes, 12),
            ReminderConfig("kids_breakfast", "Make Kids Breakfast", "15 6 * * 1-5", 5.minutes, 6),
            ReminderConfig("kids_clothes", "Kids Dressed & Ready", "30 6 * * 1-5", 5.minutes, 6),
            ReminderConfig("pack_lunches", "Pack School Lunches", "40 6 * * 1-5", 5.minutes, 6),
            ReminderConfig("school_drop", "School Drop-off", "0 7 * * 1-5", 5.minutes, 6),
            ReminderConfig("own_breakfast", "Eat Your Own Breakfast", "30 7 * * 1-5", 5.minutes, 6),
            // Daytime
            ReminderConfig("grocery_list", "Check Grocery List", "0 10 * * 1,3,5", 5.minutes, 6),
            ReminderConfig("laundry_start", "Start Laundry", "0 9 * * 1,3,5", 5.minutes, 6),
            ReminderConfig("laundry_hang", "Hang/Fold Laundry", "0 11 * * 1,3,5", 5.minutes, 6),
            ReminderConfig("school_pickup", "School Pick-up", "0 15 * * 1-5", 5.minutes, 6),
            // Afternoon/Evening
            ReminderConfig("kids_snack", "Kids Afternoon Snack", "30 15 * * 1-5", 5.minutes, 3),
            ReminderConfig("homework_help", "Help with Homework", "0 16 * * 1-4", 5.minutes, 6),
            ReminderConfig("start_dinner", "Start Making Dinner", "0 17 * * *", 5.minutes, 6),
            ReminderConfig("family_dinner", "Family Dinner Together", "0 18 * * *", 5.minutes, 3),
            ReminderConfig("kitchen_cleanup", "Kitchen Cleanup", "30 18 * * *", 5.minutes, 6),
            ReminderConfig("kids_bath", "Kids Bath Time", "0 19 * * *", 5.minutes, 6),
            ReminderConfig("bedtime_routine", "Kids Bedtime Routine", "30 19 * * *", 5.minutes, 6),
            ReminderConfig("check_school_msg", "Check School Messages", "0 20 * * 0-4", 5.minutes, 6),
            // Weekend
            ReminderConfig("weekend_activity", "Plan Weekend Activity", "0 9 * * 6", 5.minutes, 6),
            ReminderConfig("family_cleanup", "Family Cleanup Hour", "0 10 * * 0", 5.minutes, 12)
        )
        val yaml = buildPresetsYaml(configs)
        return Triple(yaml, "Parent Life (${configs.size} quests)", configs)
    }

    private fun createHealthyHabitsConfig(): Triple<String, String, List<ReminderConfig>> {
        val configs = listOf(
            // Morning
            ReminderConfig("consistent_wake", "Wake Up (same time daily)", "0 7 * * *", 5.minutes, 12),
            ReminderConfig("morning_water", "Drink Water (500ml)", "5 7 * * *", 5.minutes, 3),
            ReminderConfig("no_phone_morning", "No Phone First 30 min", "10 7 * * *", 5.minutes, 3),
            ReminderConfig("healthy_bfast", "Healthy Breakfast", "30 7 * * *", 5.minutes, 6),
            ReminderConfig("brush_teeth_am", "Brush & Floss", "0 8 * * *", 5.minutes, 6),
            ReminderConfig("daily_vitamins", "Take Vitamins", "10 8 * * *", 5.minutes, 6),
            // Daytime
            ReminderConfig("move_10min", "Move for 10 Minutes", "0 10 * * *", 5.minutes, 6),
            ReminderConfig("water_refill_1", "Refill Water Bottle", "0 11 * * *", 5.minutes, 3),
            ReminderConfig("mindful_eating", "Eat Lunch (no screen)", "0 12 * * *", 5.minutes, 6),
            ReminderConfig("outdoor_time", "Go Outside (15 min)", "0 13 * * *", 5.minutes, 6),
            ReminderConfig("water_refill_2", "Refill Water Bottle", "0 14 * * *", 5.minutes, 3),
            ReminderConfig("healthy_snack", "Healthy Snack", "0 15 * * *", 5.minutes, 3),
            ReminderConfig("movement_break", "Movement Break (stairs/walk)", "0 16 * * *", 5.minutes, 3),
            // Evening
            ReminderConfig("cook_not_order", "Cook Dinner (don't order)", "0 18 * * *", 5.minutes, 6),
            ReminderConfig("evening_walk", "Evening Walk (20 min)", "0 19 * * *", 5.minutes, 6),
            ReminderConfig("gratitude_3", "Write 3 Gratitudes", "0 21 * * *", 5.minutes, 6),
            ReminderConfig("brush_teeth_pm", "Brush & Floss", "15 21 * * *", 5.minutes, 6),
            ReminderConfig("screen_curfew", "Screens Off", "0 22 * * *", 5.minutes, 6),
            ReminderConfig("sleep_time", "Lights Out", "30 22 * * *", 5.minutes, 6),
            // Weekly
            ReminderConfig("weigh_measure", "Weekly Check-in (weight/mood)", "0 7 * * 1", 5.minutes, 6)
        )
        val yaml = buildPresetsYaml(configs)
        return Triple(yaml, "Healthy Habits (${configs.size} quests)", configs)
    }

    private fun buildPresetsYaml(configs: List<ReminderConfig>): String {
        return buildString {
            appendLine("reminders:")
            configs.forEach { config ->
                appendLine("  - id: ${config.id}")
                appendLine("    title: \"${config.title}\"")
                appendLine("    schedule: \"${config.schedule}\"")
                appendLine("    nag_interval: ${config.nagInterval.inWholeMinutes}m")
                appendLine("    max_nags: ${config.maxNags}")
            }
        }
    }
}
