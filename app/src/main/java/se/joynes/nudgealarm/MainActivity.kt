package se.joynes.nudgealarm

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.app.ApplicationExitInfo
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import se.joynes.nudgealarm.core.event.Event
import se.joynes.nudgealarm.service.ReminderService
import se.joynes.nudgealarm.storage.EventLogStore
import se.joynes.nudgealarm.ui.AnalyticsScreen
import se.joynes.nudgealarm.ui.AnalyticsViewModel
import se.joynes.nudgealarm.ui.EditRemindersScreen
import se.joynes.nudgealarm.ui.EditRemindersViewModel
import se.joynes.nudgealarm.ui.EventLogScreen
import se.joynes.nudgealarm.ui.MainScreen
import se.joynes.nudgealarm.ui.MainViewModel
import se.joynes.nudgealarm.ui.PermissionScreen
import se.joynes.nudgealarm.ui.RoutinesScreen
import se.joynes.nudgealarm.ui.SettingsScreen
import se.joynes.nudgealarm.ui.SettingsViewModel
import se.joynes.nudgealarm.ui.StatusScreen
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme
import se.joynes.nudgealarm.ui.theme.MegadriveCyan
import se.joynes.nudgealarm.ui.theme.MegadriveGold
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.joynes.nudgealarm.notification.ChannelSetup
import se.joynes.nudgealarm.storage.SettingsStore

enum class Screen {
    Main,
    Permissions,
    EventLog,
    Status,
    Routines,
    Analytics,
    Settings,
    EditReminders
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashLogger(this)
        collectNativeCrashes(this)
        enableEdgeToEdge()
        setContent {
            NudgeAlarmTheme {
                NudgeAlarmApp()
            }
        }
    }
}

private fun installCrashLogger(context: Context) {
    val eventLog = EventLogStore(context)
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        try {
            val stackTrace = throwable.stackTraceToString().take(3000)
            eventLog.add(
                Event.Crash(
                    exceptionClass = throwable::class.java.name,
                    exceptionMessage = throwable.message ?: "(no message)",
                    stackTrace = stackTrace
                )
            )
        } catch (_: Exception) {
            // Don't let crash logger crash
        }
        defaultHandler?.uncaughtException(thread, throwable)
    }
}

/**
 * Reads the last 5 process exit reasons via ApplicationExitInfo (API 30+, always available
 * since minSdk=31). Stores any CRASH or CRASH_NATIVE entries in EventLogStore so they appear
 * in the in-app crash log viewer. Uses a SharedPreferences key to avoid logging the same
 * crash twice across restarts.
 */
private fun collectNativeCrashes(context: Context) {
    try {
        val prefs = context.getSharedPreferences("crash_collector", Context.MODE_PRIVATE)
        val lastLoggedTs = prefs.getLong("last_logged_ts", 0L)
        val eventLog = EventLogStore(context)
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val exits = am.getHistoricalProcessExitReasons(null, 0, 5)
        var newest = lastLoggedTs
        for (exit in exits) {
            if (exit.timestamp <= lastLoggedTs) continue
            val isJavaCrash = exit.reason == ApplicationExitInfo.REASON_CRASH
            val isNativeCrash = exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE
            if (!isJavaCrash && !isNativeCrash) continue

            val trace = try {
                exit.traceInputStream?.bufferedReader()?.readText()?.take(3000) ?: ""
            } catch (_: Exception) { "" }

            val kind = if (isNativeCrash) "NativeCrash" else "JavaCrash(process)"
            Log.e("CrashCollector", "$kind at ${exit.timestamp}: ${exit.description}")
            eventLog.add(
                Event.Crash(
                    timestamp = exit.timestamp,
                    exceptionClass = kind,
                    exceptionMessage = exit.description ?: "Process exit reason ${exit.reason}",
                    stackTrace = trace.ifBlank { "(no trace available)" }
                )
            )
            if (exit.timestamp > newest) newest = exit.timestamp
        }
        if (newest > lastLoggedTs) {
            prefs.edit().putLong("last_logged_ts", newest).apply()
        }
    } catch (e: Exception) {
        Log.e("CrashCollector", "Failed to collect exit reasons", e)
    }
}

fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        // On older Android versions, check if notifications are enabled
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.areNotificationsEnabled()
    }
}

@Composable
fun NudgeAlarmApp() {
    val viewModel: MainViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    var currentScreen by remember { mutableStateOf(Screen.Main) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val eventLog = remember { EventLogStore(context) }
    val coroutineScope = rememberCoroutineScope()

    // Track notification permission status
    var hasNotificationPermission by remember { mutableStateOf(checkNotificationPermission(context)) }

    val settingsStore = remember { SettingsStore(context) }
    var quietMode by remember { mutableStateOf(settingsStore.quietMode) }
    var showMenuDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    LaunchedEffect(Unit) {
        eventLog.add(Event.AppForegrounded())
        eventLog.add(Event.Debug(detail = "NudgeAlarmApp composed, starting polling"))
        viewModel.startPolling()
    }

    // Re-check permission when app is foregrounded (returning from settings)
    LaunchedEffect(currentScreen) {
        hasNotificationPermission = checkNotificationPermission(context)
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.loadFromFile(it) }
    }

    val fileSaver = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-yaml")
    ) { uri ->
        uri?.let { viewModel.saveToFile(it) }
    }

    val dataExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportAllData(it) {} }
    }

    val dataImporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { pendingImportUri = it }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    val bottomBarScreens = setOf(Screen.Main, Screen.EditReminders, Screen.Analytics, Screen.Settings)
    val showBottomBar = currentScreen in bottomBarScreens

    val toggleQuietMode = {
        val newMode = !quietMode
        quietMode = newMode
        settingsStore.quietMode = newMode
        ChannelSetup.recreateReminderChannel(context)
        val intent = Intent(context, ReminderService::class.java).apply {
            action = ReminderService.ACTION_REFRESH_NOTIFICATION
        }
        context.startService(intent)
        Unit
    }

    // Import confirmation dialog — shown after user picks a file
    if (pendingImportUri != null) {
        val uri = pendingImportUri!!
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    "!! VARNING !!",
                    color = se.joynes.nudgealarm.ui.theme.MegadriveRed,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    "Import kommer RADERA all befintlig data — alla quests, historik och inställningar ersätts av filen du valt.\n\nDetta går inte att ångra.",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel.importAllData(uri) {}
                        pendingImportUri = null
                    }
                ) {
                    Text("JA, IMPORTERA", color = se.joynes.nudgealarm.ui.theme.MegadriveRed)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingImportUri = null }) {
                    Text("AVBRYT", color = MegadriveCyan)
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentScreen = currentScreen,
                    onNavigate = { screen ->
                        if (currentScreen == Screen.EditReminders && screen != Screen.EditReminders) {
                            viewModel.saveCurrentGame()
                        }
                        eventLog.add(Event.NavigatedTo(screen = screen.name))
                        currentScreen = screen
                    }
                )
            }
        }
    ) { innerPadding ->
    when (currentScreen) {
        Screen.Main -> MainScreen(
            uiState = uiState,
            hasNotificationPermission = hasNotificationPermission,
            quietMode = quietMode,
            onToggleQuietMode = toggleQuietMode,
            onMenuClick = { showMenuDialog = true },
            showMenu = showMenuDialog,
            onMenuDismiss = { showMenuDialog = false },
            onRequestNotificationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onSelectFile = {
                filePicker.launch(arrayOf("*/*"))
            },
            onSaveGame = {
                val gameName = uiState.currentGameName ?: "NudgeAlarm_Game"
                val fileName = gameName.replace(Regex("[^a-zA-Z0-9]"), "_") + ".yaml"
                fileSaver.launch(fileName)
            },
            onShareGame = {
                // Share game via system share dialog
                val gameName = uiState.currentGameName ?: "NudgeAlarm_Game"
                val fileName = gameName.replace(Regex("[^a-zA-Z0-9_åäöÅÄÖ ]"), "_").replace(" ", "_") + ".yaml"
                val internalFile = java.io.File(context.filesDir, "game_${uiState.currentGameId}.yaml")
                val shareFile = java.io.File(context.cacheDir, fileName)

                if (internalFile.exists()) {
                    internalFile.copyTo(shareFile, overwrite = true)
                } else {
                    val presetFile = java.io.File(context.filesDir, "${uiState.currentGameId}.yaml")
                    if (presetFile.exists()) {
                        presetFile.copyTo(shareFile, overwrite = true)
                    } else {
                        val customConfig = java.io.File(context.filesDir, "custom_config.yaml")
                        if (customConfig.exists()) {
                            customConfig.copyTo(shareFile, overwrite = true)
                        }
                    }
                }

                if (shareFile.exists()) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        shareFile
                    )
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "application/x-yaml"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "NudgeAlarm: $gameName")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share $gameName"))
                }
            },
            onRenameGame = { newName -> viewModel.renameCurrentGame(newName) },
            onExportToClipboard = { onResult ->
                coroutineScope.launch {
                    val yaml = viewModel.exportToClipboard()
                    onResult(yaml)
                }
            },
            onImportFromClipboard = { yaml, onResult ->
                coroutineScope.launch {
                    val result = viewModel.importFromClipboard(yaml)
                    onResult(result)
                }
            },
            onExportData = {
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
                dataExporter.launch("nudgealarm_backup_$timestamp.json")
            },
            onImportData = {
                dataImporter.launch(arrayOf("application/json", "*/*"))
            },
            onLoadPreset = { presetId -> viewModel.loadPreset(presetId) },
            onLoadSavedGame = { savedGame -> viewModel.loadSavedGame(savedGame) },
            onDeleteSavedGame = { gameId -> viewModel.deleteSavedGame(gameId) },
            onCreateNewSchema = { name, onComplete -> viewModel.createNewSchema(name, onComplete) },
            onStartService = { viewModel.startService() },
            onStopService = { viewModel.stopService() },
            onNavigateToPermissions = {
                eventLog.add(Event.NavigatedTo(screen = "Permissions"))
                currentScreen = Screen.Permissions
            },
            onNavigateToEventLog = {
                eventLog.add(Event.NavigatedTo(screen = "EventLog"))
                currentScreen = Screen.EventLog
            },
            onNavigateToRoutines = {
                eventLog.add(Event.NavigatedTo(screen = "Routines"))
                currentScreen = Screen.Routines
            },
            onNavigateToEditReminders = {
                eventLog.add(Event.NavigatedTo(screen = "EditReminders"))
                currentScreen = Screen.EditReminders
            },
            onMarkDone = { ruleId -> viewModel.markReminderDone(ruleId) },
            onMarkAllDone = { ruleIds -> viewModel.markAllRemindersDone(ruleIds) },
            onSnooze = { ruleId, minutes -> viewModel.snoozeReminder(ruleId, minutes) },
            onCancel = { ruleId -> viewModel.cancelReminder(ruleId) },
            onCancelAll = { ruleIds -> viewModel.cancelAllReminders(ruleIds) },
            modifier = Modifier.padding(innerPadding)
        )

        Screen.Permissions -> PermissionScreen(
            onRequestNotificationPermission = {
                eventLog.add(Event.UiAction(action = "Request notification permission"))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onBack = {
                eventLog.add(Event.NavigatedTo(screen = "Main (from Permissions)"))
                currentScreen = Screen.Main
            },
            modifier = Modifier.padding(innerPadding)
        )

        Screen.EventLog -> EventLogScreen(
            events = uiState.events,
            onClear = { viewModel.clearEventLog() },
            onBack = {
                eventLog.add(Event.NavigatedTo(screen = "Main (from EventLog)"))
                currentScreen = Screen.Main
            },
            modifier = Modifier.padding(innerPadding)
        )

        Screen.Status -> StatusScreen(
            isServiceRunning = uiState.isServiceRunning,
            nextTriggerRule = uiState.nextTriggerRule,
            nextTriggerTime = ReminderService.nextTriggerTime,
            lastTriggerRule = null,
            lastTriggerTime = null,
            reminders = ReminderService.currentConfig?.reminders ?: emptyList(),
            onBack = {
                eventLog.add(Event.NavigatedTo(screen = "Main (from Status)"))
                currentScreen = Screen.Main
            },
            modifier = Modifier.padding(innerPadding)
        )

        Screen.Routines -> RoutinesScreen(
            reminders = ReminderService.currentConfig?.reminders ?: emptyList(),
            onBack = {
                eventLog.add(Event.NavigatedTo(screen = "Main (from Routines)"))
                currentScreen = Screen.Main
            },
            modifier = Modifier.padding(innerPadding)
        )

        Screen.Analytics -> {
            val analyticsViewModel: AnalyticsViewModel = viewModel()
            val analyticsState by analyticsViewModel.uiState.collectAsState()
            AnalyticsScreen(
                uiState = analyticsState,
                onSelectTimeRange = { range -> analyticsViewModel.selectTimeRange(range) },
                onBack = {
                    eventLog.add(Event.NavigatedTo(screen = "Main (from Analytics)"))
                    currentScreen = Screen.Main
                },
                modifier = Modifier.padding(innerPadding)
            )
        }

        Screen.Settings -> {
            val settingsViewModel: SettingsViewModel = viewModel()
            val settingsState by settingsViewModel.uiState.collectAsState()
            SettingsScreen(
                uiState = settingsState,
                onSelectAlarmSound = { sound -> settingsViewModel.setAlarmSound(sound) },
                onSelectCustomSound = { name, uri -> settingsViewModel.setCustomSound(name, uri) },
                onToggleVibration = { enabled -> settingsViewModel.setVibrationEnabled(enabled) },
                onPreviewSound = { uri -> settingsViewModel.previewSound(uri) },
                onStopPreview = { settingsViewModel.stopPreview() },
                onNavigateToPermissions = {
                    settingsViewModel.stopPreview()
                    eventLog.add(Event.NavigatedTo(screen = "Permissions (from Settings)"))
                    currentScreen = Screen.Permissions
                },
                onSetOldReminderRetentionMinutes = { minutes -> settingsViewModel.setOldReminderRetentionMinutes(minutes) },
                onToggleAlertOnlyWhenActive = { enabled -> settingsViewModel.setAlertOnlyWhenActive(enabled) },
                onSetMinAlertIntervalMinutes = { minutes -> settingsViewModel.setMinAlertIntervalMinutes(minutes) },
                onBack = {
                    settingsViewModel.stopPreview()
                    eventLog.add(Event.NavigatedTo(screen = "Main (from Settings)"))
                    currentScreen = Screen.Main
                },
                modifier = Modifier.padding(innerPadding)
            )
        }

        Screen.EditReminders -> {
            val editRemindersViewModel: EditRemindersViewModel = viewModel()
            val editRemindersState by editRemindersViewModel.uiState.collectAsState()

            // Refresh data every time this screen is shown
            LaunchedEffect(Unit) {
                editRemindersViewModel.refresh()
            }

            EditRemindersScreen(
                uiState = editRemindersState,
                onAddNew = { editRemindersViewModel.startAddNew() },
                onEdit = { reminder -> editRemindersViewModel.startEdit(reminder) },
                onDelete = { id -> editRemindersViewModel.deleteReminder(id) },
                onToggleEnabled = { id -> editRemindersViewModel.toggleEnabled(id) },
                onSave = { title, schedule, nagInterval, maxNags, sticky ->
                    editRemindersViewModel.saveReminder(title, schedule, nagInterval, maxNags, sticky)
                },
                onCancelEdit = { editRemindersViewModel.cancelEdit() },
                onBack = {
                    // Auto-save the game when leaving editor
                    viewModel.saveCurrentGame()
                    eventLog.add(Event.NavigatedTo(screen = "Main (from EditReminders)"))
                    currentScreen = Screen.Main
                },
                onLoadFromFile = {
                    filePicker.launch(arrayOf("*/*"))
                },
                onSaveToFile = {
                    val gameName = uiState.currentGameName ?: "NudgeAlarm_Game"
                    val fileName = gameName.replace(Regex("[^a-zA-Z0-9]"), "_") + ".yaml"
                    fileSaver.launch(fileName)
                },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    }
}

@Composable
private fun BottomNavBar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit
) {
    NavigationBar(
        containerColor = Color(0xFF1A1A2E)
    ) {
        NavigationBarItem(
            selected = currentScreen == Screen.Main,
            onClick = { onNavigate(Screen.Main) },
            icon = { Icon(Icons.Filled.Home, contentDescription = "Quests") },
            label = { Text("QUESTS", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MegadriveGold,
                selectedTextColor = MegadriveGold,
                indicatorColor = MegadriveGold.copy(alpha = 0.15f),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            selected = currentScreen == Screen.EditReminders,
            onClick = { onNavigate(Screen.EditReminders) },
            icon = { Icon(Icons.Filled.Edit, contentDescription = "Edit") },
            label = { Text("EDIT", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MegadriveCyan,
                selectedTextColor = MegadriveCyan,
                indicatorColor = MegadriveCyan.copy(alpha = 0.15f),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            selected = currentScreen == Screen.Analytics,
            onClick = { onNavigate(Screen.Analytics) },
            icon = { Icon(Icons.Filled.Star, contentDescription = "History") },
            label = { Text("SCORES", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MegadriveGold,
                selectedTextColor = MegadriveGold,
                indicatorColor = MegadriveGold.copy(alpha = 0.15f),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
        NavigationBarItem(
            selected = currentScreen == Screen.Settings,
            onClick = { onNavigate(Screen.Settings) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
            label = { Text("OPTIONS", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MegadriveGold,
                selectedTextColor = MegadriveGold,
                indicatorColor = MegadriveGold.copy(alpha = 0.15f),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )
    }
}
