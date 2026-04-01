package org.nudgealarm.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nudgealarm.app.R
import org.nudgealarm.app.ui.theme.MegadriveGold
import org.nudgealarm.app.ui.theme.MegadriveGreen
import org.nudgealarm.app.ui.theme.MegadriveRed
import org.nudgealarm.app.ui.theme.MegadriveCyan
import org.nudgealarm.app.ui.theme.MegadriveOrange
import org.nudgealarm.app.ui.theme.MegadrivePurple
import org.nudgealarm.app.ui.components.ButtonSound
import org.nudgealarm.app.ui.components.FeedbackButton
import org.nudgealarm.app.ui.components.FeedbackOutlinedButton
import org.nudgealarm.app.audio.SoundManager
import org.nudgealarm.app.core.cron.CronExpression

val APP_VERSION = org.nudgealarm.app.BuildConfig.VERSION_CODE

data class PresetConfig(
    val id: String,
    val name: String,
    val description: String
)

val PRESET_CONFIGS = listOf(
    PresetConfig("fitness_focus", ">> FITNESS FOCUS", "20 quests - workouts, protein, hydration & meal prep"),
    PresetConfig("student_life", ">> STUDENT LIFE", "22 quests - study blocks, breaks, homework & routines"),
    PresetConfig("self_care", ">> SELF CARE", "24 quests - skincare, vitamins, meditation & journaling"),
    PresetConfig("productivity", ">> PRODUCTIVITY", "20 quests - deep work, inbox zero & weekly reviews"),
    PresetConfig("parent_life", ">> PARENT LIFE", "20 quests - kids, meals, school & household"),
    PresetConfig("healthy_habits", ">> HEALTHY HABITS", "20 quests - sleep, nutrition, movement & mindfulness"),
    PresetConfig("test_1min", ">> QUICK TEST", "Triggers in 1 minute - test your reflexes!"),
    PresetConfig("test_hourly", ">> HOURLY BOSS", "Battle every hour on the hour")
)

@Composable
fun MainScreen(
    uiState: MainUiState,
    hasNotificationPermission: Boolean,
    quietMode: Boolean,
    onToggleQuietMode: () -> Unit,
    onMenuClick: () -> Unit,
    showMenu: Boolean,
    onMenuDismiss: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onSelectFile: () -> Unit,
    onSaveGame: () -> Unit,
    onShareGame: () -> Unit,
    onRenameGame: (String) -> Unit,
    onExportToClipboard: (onResult: (String?) -> Unit) -> Unit,
    onImportFromClipboard: (yaml: String, onResult: (Result<Int>) -> Unit) -> Unit,
    onExportData: () -> Unit,
    onImportData: () -> Unit,
    onLoadPreset: (String) -> Unit,
    onLoadSavedGame: (org.nudgealarm.app.storage.SavedGame) -> Unit,
    onDeleteSavedGame: (String) -> Unit,
    onCreateNewSchema: (name: String, onComplete: () -> Unit) -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToEventLog: () -> Unit,
    onNavigateToRoutines: () -> Unit,
    onNavigateToEditReminders: () -> Unit,
    onMarkDone: (String) -> Unit,
    onSnooze: (String, Int) -> Unit,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPresetDialog by remember { mutableStateOf(false) }
    var showNewGameDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var showExportSuccessDialog by remember { mutableStateOf(false) }
    var showImportResultDialog by remember { mutableStateOf<String?>(null) }
    var newGameName by remember { mutableStateOf("") }
    var renameGameName by remember { mutableStateOf("") }
    var showCompleteAllConfirm by remember { mutableStateOf(false) }
    var showAbandonAllConfirm by remember { mutableStateOf(false) }
    var activeAllButtonShowsAbandon by remember { mutableStateOf(false) }
    var showCompleteRemainingConfirm by remember { mutableStateOf(false) }
    var showSnoozeAllActive by remember { mutableStateOf(false) }
    var showSnoozeAllRemaining by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // === HEADER - Like a game title screen ===
        RetroHeader(
            isRunning = uiState.isServiceRunning,
            currentGameName = uiState.currentGameName,
            quietMode = quietMode,
            onToggleQuietMode = onToggleQuietMode,
            onMenuClick = onMenuClick
        )

        // === PERMISSION WARNING - Very prominent! ===
        if (!hasNotificationPermission) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 3.dp,
                        color = MegadriveRed,
                        shape = RoundedCornerShape(4.dp)
                    ),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MegadriveRed.copy(alpha = 0.15f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "!! WARNING !!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MegadriveRed
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "NOTIFICATIONS DISABLED",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MegadriveOrange
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The app cannot remind you without notification permission. Quests will be silent!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MegadriveGreen
                        )
                    ) {
                        Text(
                            text = ">> ENABLE NOTIFICATIONS",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // === ACTIVE QUESTS - Needs immediate action! ===
        if (uiState.activeReminders.isNotEmpty()) {
            RetroCard(
                borderColor = MegadriveRed
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "! ",
                                color = MegadriveRed,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "ACTIVE QUESTS",
                                style = MaterialTheme.typography.titleMedium,
                                color = MegadriveRed
                            )
                            Text(
                                text = " [${uiState.activeReminders.size}]",
                                style = MaterialTheme.typography.titleMedium,
                                color = MegadriveOrange
                            )
                        }
                        if (uiState.activeReminders.size > 1) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(
                                    onClick = { showSnoozeAllActive = true },
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, MegadriveCyan),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("SNOOZE ALL", style = MaterialTheme.typography.labelSmall, color = MegadriveCyan)
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (activeAllButtonShowsAbandon) {
                                            showAbandonAllConfirm = true
                                            activeAllButtonShowsAbandon = false
                                        } else {
                                            showCompleteAllConfirm = true
                                        }
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, if (activeAllButtonShowsAbandon) MegadriveOrange else MegadriveGreen),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        if (activeAllButtonShowsAbandon) "ABANDON ALL" else "COMPLETE ALL",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (activeAllButtonShowsAbandon) MegadriveOrange else MegadriveGreen
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = "Complete these to level up!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    uiState.activeReminders.forEach { reminder ->
                        ActiveQuestCard(
                            reminder = reminder,
                            onDone = { onMarkDone(reminder.ruleId) },
                            onSnooze = { minutes -> onSnooze(reminder.ruleId, minutes) },
                            onCancel = { onCancel(reminder.ruleId) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // === TODAY'S SCHEDULE - Upcoming battles ===
        if (uiState.todaysSchedule.isNotEmpty()) {
            RetroCard(
                borderColor = MegadriveCyan
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "> ",
                            color = MegadriveCyan,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "QUEST LOG",
                            style = MaterialTheme.typography.titleMedium,
                            color = MegadriveCyan
                        )
                        Text(
                            text = " [${uiState.todaysSchedule.size}]",
                            style = MaterialTheme.typography.titleMedium,
                            color = MegadriveGold
                        )
                    }
                    Text(
                        text = "Upcoming battles today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    uiState.todaysSchedule.forEach { scheduled ->
                        ScheduledQuestRow(
                            scheduled = scheduled,
                            onMarkDone = { onMarkDone(scheduled.ruleId) },
                            onSnooze = { minutes -> onSnooze(scheduled.ruleId, minutes) },
                            onCancel = { onCancel(scheduled.ruleId) }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCompleteRemainingConfirm = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, MegadriveGreen),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text("COMPLETE ALL", style = MaterialTheme.typography.labelSmall, color = MegadriveGreen)
                        }
                        OutlinedButton(
                            onClick = { showSnoozeAllRemaining = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, MegadriveCyan),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text("SNOOZE ALL", style = MaterialTheme.typography.labelSmall, color = MegadriveCyan)
                        }
                        OutlinedButton(
                            onClick = { showAbandonAllConfirm = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, MegadriveOrange),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text("ABANDON ALL", style = MaterialTheme.typography.labelSmall, color = MegadriveOrange)
                        }
                    }
                }
            }
        } else if (uiState.isServiceRunning) {
            RetroCard(
                borderColor = MegadriveGreen
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "* STAGE CLEAR *",
                        style = MaterialTheme.typography.titleMedium,
                        color = MegadriveGreen
                    )
                    Text(
                        text = "All quests completed for today!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // === QUICK START - Game select screen ===
        if (!uiState.isServiceRunning) {
            RetroCard(
                borderColor = MegadriveGold
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.hasGameLoaded && uiState.currentGameName != null) {
                        // Game is loaded - show current game info
                        Text(
                            text = ">> GAME LOADED",
                            style = MaterialTheme.typography.titleMedium,
                            color = MegadriveGreen
                        )
                        Text(
                            text = uiState.currentGameName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MegadriveGold
                        )
                        Text(
                            text = "${uiState.totalRulesCount} quests ready",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        RetroButton(
                            text = "EDIT QUESTS",
                            onClick = onNavigateToEditReminders,
                            color = MegadriveCyan
                        )

                        RetroButton(
                            text = "CHANGE GAME",
                            onClick = { showPresetDialog = true },
                            color = MegadrivePurple
                        )

                        if (uiState.totalRulesCount > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 4.dp))
                            RetroButton(
                                text = ">> PRESS START",
                                onClick = onStartService,
                                color = MegadriveGreen,
                                sound = ButtonSound.START
                            )
                        }
                    } else {
                        // No game loaded - prompt to select
                        Text(
                            text = ">> SELECT GAME",
                            style = MaterialTheme.typography.titleMedium,
                            color = MegadriveGold
                        )
                        Text(
                            text = "Choose your adventure to begin",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        RetroButton(
                            text = "SELECT GAME",
                            onClick = { showPresetDialog = true },
                            color = MegadrivePurple
                        )
                    }
                }
            }
        }
    }

    // === MENU DIALOG - Pause menu ===
    if (showMenu) {
        AlertDialog(
            onDismissRequest = onMenuDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "= PAUSE MENU =",
                    style = MaterialTheme.typography.titleLarge,
                    color = MegadriveGold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column {
                    if (uiState.isServiceRunning) {
                        RetroMenuItem("STOP GAME") {
                            onStopService()
                            onMenuDismiss()
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    if (uiState.hasGameLoaded || uiState.isServiceRunning) {
                        RetroMenuItem("RENAME GAME") {
                            renameGameName = uiState.currentGameName ?: ""
                            onMenuDismiss()
                            showRenameDialog = true
                        }
                    }
                    RetroMenuItem("SELECT GAME") {
                        onMenuDismiss()
                        showPresetDialog = true
                    }
                    RetroMenuItem("LOAD FROM FILE") {
                        onSelectFile()
                        onMenuDismiss()
                    }
                    // Only show Save/Share if a game is loaded with quests
                    if ((uiState.hasGameLoaded || uiState.isServiceRunning) && uiState.totalRulesCount > 0) {
                        RetroMenuItem("SAVE / SHARE") {
                            onMenuDismiss()
                            showSaveDialog = true
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline
                    )
                    RetroMenuItem("EVENT LOG [${uiState.events.size}]") {
                        onNavigateToEventLog()
                        onMenuDismiss()
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline
                    )
                    RetroMenuItem("EXPORT DATA") {
                        onMenuDismiss()
                        onExportData()
                    }
                    RetroMenuItem("IMPORT DATA") {
                        onMenuDismiss()
                        onImportData()
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onMenuDismiss) {
                    Text("< BACK", color = MegadriveCyan)
                }
            }
        )
    }

    // === PRESET DIALOG - Game select ===
    var gameToDelete by remember { mutableStateOf<org.nudgealarm.app.storage.SavedGame?>(null) }

    if (showPresetDialog) {
        AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "= SELECT GAME =",
                    style = MaterialTheme.typography.titleLarge,
                    color = MegadriveGold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    // NEW GAME option - create empty schema
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPresetDialog = false
                                newGameName = ""
                                showNewGameDialog = true
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = ">> NEW GAME",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MegadriveGreen
                        )
                        Text(
                            text = "Start fresh - create your own quests!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // SAVED GAMES section (if any)
                    if (uiState.savedGames.isNotEmpty()) {
                        HorizontalDivider(color = MegadriveGold, thickness = 2.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "YOUR GAMES:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        uiState.savedGames.forEach { savedGame ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            onLoadSavedGame(savedGame)
                                            showPresetDialog = false
                                        }
                                ) {
                                    Text(
                                        text = "> ${savedGame.name}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MegadrivePurple
                                    )
                                    Text(
                                        text = "${savedGame.questCount} quests",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Delete button
                                OutlinedButton(
                                    onClick = { gameToDelete = savedGame },
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, MegadriveRed),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("X", fontSize = 12.sp, color = MegadriveRed)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    // PRESETS section
                    HorizontalDivider(color = MegadriveGold, thickness = 2.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "PRESETS:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    PRESET_CONFIGS.forEachIndexed { index, preset ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLoadPreset(preset.id)
                                    showPresetDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MegadriveCyan
                            )
                            Text(
                                text = preset.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (index < PRESET_CONFIGS.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPresetDialog = false }) {
                    Text("< BACK", color = MegadriveCyan)
                }
            }
        )
    }

    // === DELETE GAME CONFIRMATION ===
    gameToDelete?.let { game ->
        AlertDialog(
            onDismissRequest = { gameToDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "! DELETE GAME !",
                    style = MaterialTheme.typography.titleLarge,
                    color = MegadriveRed,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Remove \"${game.name}\" from your games list?\n\nThis only removes it from the list, not from disk.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSavedGame(game.id)
                        gameToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MegadriveRed),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("DELETE", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { gameToDelete = null }) {
                    Text("< CANCEL", color = MegadriveCyan)
                }
            }
        )
    }

    // === NEW GAME NAME DIALOG ===
    if (showNewGameDialog) {
        AlertDialog(
            onDismissRequest = { showNewGameDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = ">> NEW GAME",
                    style = MaterialTheme.typography.titleLarge,
                    color = MegadriveGold,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter a name for your game:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = newGameName,
                        onValueChange = { newGameName = it },
                        label = { Text("Game Name") },
                        placeholder = { Text("My Routines") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MegadriveCyan,
                            focusedLabelColor = MegadriveCyan
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newGameName.trim().ifEmpty { "New Game" }
                        showNewGameDialog = false
                        onCreateNewSchema(name) {
                            onNavigateToEditReminders()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MegadriveGreen),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("CREATE", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewGameDialog = false }) {
                    Text("< CANCEL", color = MegadriveCyan)
                }
            }
        )
    }

    // === SAVE / SHARE DIALOG ===
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "= EXPORT GAME =",
                    style = MaterialTheme.typography.titleLarge,
                    color = MegadriveGold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "\"${uiState.currentGameName ?: "Game"}\"",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MegadriveCyan,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "${uiState.totalRulesCount} quests",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Share option
                    RetroButton(
                        text = ">> SHARE",
                        onClick = {
                            showSaveDialog = false
                            onShareGame()
                        },
                        color = MegadriveGreen
                    )

                    // Save to file option
                    RetroButton(
                        text = ">> SAVE TO FILE",
                        onClick = {
                            showSaveDialog = false
                            onSaveGame()
                        },
                        color = MegadrivePurple
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "CLIPBOARD (for AI editing)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Copy to clipboard
                    RetroButton(
                        text = ">> COPY TO CLIPBOARD",
                        onClick = {
                            onExportToClipboard { yaml ->
                                if (yaml != null) {
                                    clipboardManager.setText(AnnotatedString(yaml))
                                    showSaveDialog = false
                                    showExportSuccessDialog = true
                                }
                            }
                        },
                        color = MegadriveCyan
                    )

                    // Paste from clipboard
                    RetroButton(
                        text = ">> PASTE FROM CLIPBOARD",
                        onClick = {
                            showSaveDialog = false
                            showImportConfirmDialog = true
                        },
                        color = MegadriveOrange
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("< BACK", color = MegadriveCyan)
                }
            }
        )
    }

    // === EXPORT SUCCESS DIALOG ===
    if (showExportSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showExportSuccessDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "COPIED!",
                    style = MaterialTheme.typography.titleLarge,
                    color = MegadriveGreen,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Game copied to clipboard as YAML.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Paste into ChatGPT, Claude or another AI to edit your quests. Then copy the result and use PASTE FROM CLIPBOARD to import!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MegadriveCyan
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showExportSuccessDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MegadriveGreen),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("OK", color = Color.White)
                }
            }
        )
    }

    // === IMPORT CONFIRM DIALOG ===
    if (showImportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "! IMPORT WARNING !",
                    style = MaterialTheme.typography.titleLarge,
                    color = MegadriveOrange,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "This will REPLACE all existing quests with the YAML content from your clipboard.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Make sure you have copied/exported first if you want to keep your current quests!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MegadriveOrange
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboardText = clipboardManager.getText()?.text ?: ""
                        if (clipboardText.isNotBlank()) {
                            onImportFromClipboard(clipboardText) { result ->
                                result.fold(
                                    onSuccess = { count ->
                                        showImportResultDialog = "Imported $count quests successfully!"
                                    },
                                    onFailure = { error ->
                                        showImportResultDialog = "Import failed: ${error.message}"
                                    }
                                )
                            }
                        } else {
                            showImportResultDialog = "Clipboard is empty!"
                        }
                        showImportConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MegadriveOrange),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("IMPORT NOW", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmDialog = false }) {
                    Text("< CANCEL", color = MegadriveCyan)
                }
            }
        )
    }

    // === IMPORT RESULT DIALOG ===
    showImportResultDialog?.let { message ->
        val isSuccess = message.contains("successfully")
        AlertDialog(
            onDismissRequest = { showImportResultDialog = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = if (isSuccess) "IMPORT SUCCESS!" else "IMPORT FAILED",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isSuccess) MegadriveGreen else MegadriveRed,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = { showImportResultDialog = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSuccess) MegadriveGreen else MegadriveRed
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("OK", color = Color.White)
                }
            }
        )
    }

    // === RENAME GAME DIALOG ===
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = ">> RENAME GAME",
                    style = MaterialTheme.typography.titleLarge,
                    color = MegadriveGold,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter a new name for your game:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = renameGameName,
                        onValueChange = { renameGameName = it },
                        label = { Text("Game Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MegadriveCyan,
                            focusedLabelColor = MegadriveCyan
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = renameGameName.trim()
                        if (name.isNotEmpty()) {
                            onRenameGame(name)
                        }
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MegadriveGreen),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("SAVE", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("< CANCEL", color = MegadriveCyan)
                }
            }
        )
    }

    // === COMPLETE ALL CONFIRMATION ===
    if (showCompleteAllConfirm) {
        AlertDialog(
            onDismissRequest = {
                showCompleteAllConfirm = false
                activeAllButtonShowsAbandon = true
            },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "COMPLETE ALL?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MegadriveGreen,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Mark all ${uiState.activeReminders.size} active quests as completed?",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        uiState.activeReminders.forEach { onMarkDone(it.ruleId) }
                        showCompleteAllConfirm = false
                        activeAllButtonShowsAbandon = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MegadriveGreen),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("COMPLETE ALL", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCompleteAllConfirm = false
                    activeAllButtonShowsAbandon = true
                }) {
                    Text("ABANDON ALL? >", color = MegadriveOrange)
                }
            }
        )
    }

    // === COMPLETE ALL REMAINING CONFIRMATION ===
    if (showCompleteRemainingConfirm) {
        AlertDialog(
            onDismissRequest = { showCompleteRemainingConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "COMPLETE ALL?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MegadriveGreen,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Mark all ${uiState.todaysSchedule.size} remaining quests as completed?",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        uiState.todaysSchedule.forEach { onMarkDone(it.ruleId) }
                        showCompleteRemainingConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MegadriveGreen),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("COMPLETE ALL", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteRemainingConfirm = false }) {
                    Text("< CANCEL", color = MegadriveCyan)
                }
            }
        )
    }

    // === ABANDON ALL CONFIRMATION ===
    if (showAbandonAllConfirm) {
        AlertDialog(
            onDismissRequest = {
                showAbandonAllConfirm = false
                activeAllButtonShowsAbandon = false
            },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "ABANDON ALL?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MegadriveOrange,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Abandon all ${uiState.activeReminders.size} active quests?",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        uiState.activeReminders.forEach { onCancel(it.ruleId) }
                        showAbandonAllConfirm = false
                        activeAllButtonShowsAbandon = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MegadriveOrange),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("ABANDON ALL", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAbandonAllConfirm = false
                    activeAllButtonShowsAbandon = false
                }) {
                    Text("< CANCEL", color = MegadriveCyan)
                }
            }
        )
    }

    // === SNOOZE ALL ACTIVE ===
    if (showSnoozeAllActive) {
        SnoozeAllDialog(
            title = "SNOOZE ALL ACTIVE",
            count = uiState.activeReminders.size,
            onSnooze = { minutes ->
                uiState.activeReminders.forEach { onSnooze(it.ruleId, minutes) }
                showSnoozeAllActive = false
            },
            onDismiss = { showSnoozeAllActive = false }
        )
    }

    // === SNOOZE ALL REMAINING ===
    if (showSnoozeAllRemaining) {
        SnoozeAllDialog(
            title = "SNOOZE ALL",
            count = uiState.todaysSchedule.size,
            onSnooze = { minutes ->
                uiState.todaysSchedule.forEach { onSnooze(it.ruleId, minutes) }
                showSnoozeAllRemaining = false
            },
            onDismiss = { showSnoozeAllRemaining = false }
        )
    }
}

@Composable
private fun SnoozeAllDialog(
    title: String,
    count: Int,
    onSnooze: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MegadriveCyan,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Snooze all $count quests for:",
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(15 to "15M", 60 to "1H", 180 to "3H").forEach { (minutes, label) ->
                        OutlinedButton(
                            onClick = { onSnooze(minutes) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, MegadriveCyan),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text(label, fontSize = 14.sp, color = MegadriveCyan)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(360 to "6H", 720 to "12H", 1200 to "20H").forEach { (minutes, label) ->
                        OutlinedButton(
                            onClick = { onSnooze(minutes) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, MegadriveCyan),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text(label, fontSize = 14.sp, color = MegadriveCyan)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("< CANCEL", color = MegadriveCyan)
            }
        }
    )
}

@Composable
private fun RetroHeader(
    isRunning: Boolean,
    currentGameName: String?,
    quietMode: Boolean,
    onToggleQuietMode: () -> Unit,
    onMenuClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Mascot
            Image(
                painter = painterResource(id = R.drawable.mascot),
                contentDescription = "Nudge mascot",
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "NUDGEALARM",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MegadriveGold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (isRunning) MegadriveGreen else MegadriveRed,
                                RoundedCornerShape(4.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRunning) "GAME ON" else "STANDBY",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isRunning) MegadriveGreen else MegadriveRed
                    )
                }
                // Show current game name
                if (currentGameName != null) {
                    Text(
                        text = currentGameName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MegadriveCyan
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleQuietMode, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (quietMode) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                    contentDescription = if (quietMode) "Unmute" else "Mute",
                    tint = if (quietMode) Color(0xFFFF4444) else Color.Gray,
                    modifier = Modifier.size(22.dp)
                )
            }
            Button(
                onClick = onMenuClick,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MegadrivePurple),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text("MENU", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
    }
}

@Composable
private fun RetroCard(
    borderColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(4.dp)
            ),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        content()
    }
}

@Composable
private fun RetroButton(
    text: String,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    sound: ButtonSound = ButtonSound.CLICK
) {
    FeedbackButton(
        text = text,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = color,
        sound = sound
    )
}

@Composable
private fun RetroMenuItem(
    text: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Track "just clicked" state for flash effect
    var justClicked by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(justClicked) {
        if (justClicked) {
            kotlinx.coroutines.delay(150)
            justClicked = false
        }
    }

    // Play sound when pressed
    androidx.compose.runtime.LaunchedEffect(isPressed) {
        if (isPressed) {
            SoundManager.playMenuSelect()
        }
    }

    val isHighlighted = isPressed || justClicked

    Text(
        text = "> $text",
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = {
                    justClicked = true
                    onClick()
                },
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current
            )
            .background(if (isHighlighted) MegadriveCyan.copy(alpha = 0.4f) else Color.Transparent)
            .padding(vertical = 10.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = if (isHighlighted) MegadriveCyan else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal
    )
}

@Composable
private fun ActiveQuestCard(
    reminder: ActiveReminderUi,
    onDone: () -> Unit,
    onSnooze: (Int) -> Unit,
    onCancel: () -> Unit
) {
    val wasSnoozed = reminder.snoozeCount > 0
    val borderColor = if (wasSnoozed) MegadrivePurple else MegadriveOrange

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (wasSnoozed) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(4.dp)
            ),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (wasSnoozed) MegadrivePurple.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "! ${reminder.title.uppercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (wasSnoozed) MegadrivePurple else MegadriveOrange,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = reminder.triggeredAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (wasSnoozed) {
                Text(
                    text = "Snoozed ${reminder.snoozeCount}x",
                    style = MaterialTheme.typography.labelSmall,
                    color = MegadrivePurple
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            // COMPLETE and ABANDON buttons on same row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FeedbackButton(
                    onClick = onDone,
                    modifier = Modifier.weight(1f),
                    color = MegadriveGreen,
                    sound = ButtonSound.SUCCESS,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("COMPLETE!", fontSize = 12.sp)
                }
                FeedbackOutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(0.6f),
                    color = MegadriveRed,
                    sound = ButtonSound.CANCEL,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text("ABANDON", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Snooze buttons with press feedback
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(15 to "15M", 60 to "1H", 180 to "3H", 1200 to "20H").forEach { (minutes, label) ->
                    FeedbackOutlinedButton(
                        onClick = { onSnooze(minutes) },
                        modifier = Modifier.weight(1f),
                        color = MegadriveCyan,
                        contentPadding = PaddingValues(2.dp)
                    ) {
                        Text(label, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledQuestRow(
    scheduled: ScheduledReminderUi,
    onMarkDone: () -> Unit,
    onSnooze: (Int) -> Unit,
    onCancel: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val accentColor = remember(scheduled.schedule) { questFrequencyColor(scheduled.schedule) }
    val readableSchedule = remember(scheduled.schedule) { questScheduleReadable(scheduled.schedule) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showMenu = true }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (scheduled.isSnoozed) MegadrivePurple else accentColor,
                    shape = RoundedCornerShape(4.dp)
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        // Quest info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = scheduled.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (scheduled.isSnoozed) MegadrivePurple else MaterialTheme.colorScheme.onSurface
            )
            if (readableSchedule.isNotEmpty()) {
                Text(
                    text = readableSchedule,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (scheduled.isSnoozed) MegadrivePurple.copy(alpha = 0.7f) else accentColor,
                    fontSize = 11.sp
                )
            }
        }
        // Time
        Text(
            text = scheduled.scheduledTime,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (scheduled.isSnoozed) MegadrivePurple else MegadriveGold,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        // Quick complete button
        IconButton(
            onClick = onMarkDone,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Complete quest",
                tint = MegadriveGreen,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    // Quest action menu dialog
    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Column {
                    Text(
                        text = scheduled.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MegadriveGold,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Scheduled: ${scheduled.scheduledTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Mark Done
                    FeedbackButton(
                        text = "QUEST COMPLETE!",
                        onClick = {
                            showMenu = false
                            onMarkDone()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = MegadriveGreen,
                        sound = ButtonSound.SUCCESS
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "SNOOZE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MegadriveCyan
                    )

                    // Snooze options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            15 to "15M",
                            60 to "1H",
                            180 to "3H"
                        ).forEach { (minutes, label) ->
                            FeedbackOutlinedButton(
                                onClick = {
                                    showMenu = false
                                    onSnooze(minutes)
                                },
                                modifier = Modifier.weight(1f),
                                color = MegadriveCyan,
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                Text(label, fontSize = 12.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            360 to "6H",
                            720 to "12H",
                            1200 to "20H"
                        ).forEach { (minutes, label) ->
                            FeedbackOutlinedButton(
                                onClick = {
                                    showMenu = false
                                    onSnooze(minutes)
                                },
                                modifier = Modifier.weight(1f),
                                color = MegadriveCyan,
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                Text(label, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Abandon
                    FeedbackOutlinedButton(
                        text = "ABANDON QUEST",
                        onClick = {
                            showMenu = false
                            onCancel()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = MegadriveRed,
                        sound = ButtonSound.CANCEL
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMenu = false }) {
                    Text("< BACK", color = MegadriveCyan)
                }
            }
        )
    }
}

/**
 * Determine accent color based on schedule frequency.
 */
private fun questFrequencyColor(schedule: String): Color {
    if (schedule.isEmpty()) return MegadriveCyan
    if (schedule.startsWith("once:")) return MegadrivePurple
    return try {
        val cron = CronExpression.parse(schedule)
        val next1 = cron.nextTriggerTime()
        val next2 = cron.nextTriggerTime(next1 + 60_000)
        val intervalHours = (next2 - next1) / 3_600_000.0
        when {
            intervalHours < 1 -> MegadriveRed
            intervalHours < 24 -> MegadriveOrange
            intervalHours < 36 -> MegadriveGold
            intervalHours < 24 * 7 -> MegadriveCyan
            intervalHours < 24 * 32 -> MegadriveGreen
            else -> MegadrivePurple
        }
    } catch (_: Exception) {
        MegadriveCyan
    }
}

/**
 * Convert cron schedule to human-readable text for quest log.
 */
private fun questScheduleReadable(schedule: String): String {
    if (schedule.isEmpty()) return ""
    if (schedule.startsWith("once:")) {
        val timestamp = schedule.removePrefix("once:").toLongOrNull() ?: return ""
        val dateFormat = java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.ENGLISH)
        return "One-time ${dateFormat.format(java.util.Date(timestamp))}"
    }

    val parts = schedule.trim().split(Regex("\\s+"))
    if (parts.size != 5) return schedule

    val minute = parts[0].toIntOrNull() ?: return schedule
    val hour = parts[1].toIntOrNull() ?: return schedule
    val dayOfMonth = parts[2]
    val monthField = parts[3]
    val dayOfWeek = parts[4]

    val timeStr = String.format("%02d:%02d", hour, minute)

    // Monthly schedule
    if (dayOfMonth != "*" && dayOfWeek == "*") {
        val monthNames = listOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val monthStr = if (monthField == "*") "" else {
            val months = monthField.split(",").mapNotNull { it.toIntOrNull() }
            " " + months.joinToString(",") { monthNames.getOrElse(it) { it.toString() } }
        }
        return "Day $dayOfMonth$monthStr $timeStr"
    }

    // Advanced: both specific
    if (dayOfMonth != "*" && dayOfWeek != "*") return schedule

    val daysStr = when (dayOfWeek) {
        "*" -> "Every day"
        "1-5" -> "Weekdays"
        "0,6" -> "Weekends"
        else -> {
            val dayNames = mapOf(0 to "Sun", 1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat")
            if (dayOfWeek.contains(",")) {
                dayOfWeek.split(",").mapNotNull { dayNames[it.toIntOrNull()] }.joinToString(", ")
            } else {
                dayNames[dayOfWeek.toIntOrNull()] ?: dayOfWeek
            }
        }
    }

    return "$daysStr $timeStr"
}
