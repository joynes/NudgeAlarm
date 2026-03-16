package org.nudgealarm.app.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nudgealarm.app.storage.AlarmSound
import org.nudgealarm.app.ui.theme.MegadriveCyan
import org.nudgealarm.app.ui.theme.MegadriveGold
import org.nudgealarm.app.ui.theme.MegadriveGreen
import org.nudgealarm.app.ui.theme.MegadriveOrange
import org.nudgealarm.app.ui.theme.MegadrivePurple
import org.nudgealarm.app.ui.components.ButtonSound
import org.nudgealarm.app.ui.components.FeedbackButton
import org.nudgealarm.app.ui.components.FeedbackOutlinedButton

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onSelectAlarmSound: (AlarmSound) -> Unit,
    onSelectCustomSound: (String, Uri) -> Unit,
    onToggleVibration: (Boolean) -> Unit,
    onPreviewSound: (Uri?) -> Unit,
    onStopPreview: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAllRingtones by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // === HEADER - Options Screen ===
        Column {
            Text(
                text = "OPTIONS",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MegadriveGold
            )
            Text(
                text = "Configure your game settings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // === SOUND SETTINGS - Audio Configuration ===
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, MegadrivePurple, RoundedCornerShape(4.dp)),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = ">> SOUND TEST",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MegadrivePurple
                    )
                    Text(
                        text = "Select your battle theme",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Show if custom sound is selected
                    if (uiState.customSoundName != null) {
                        AlarmSoundOption(
                            name = uiState.customSoundName,
                            isSelected = true,
                            isPlaying = uiState.currentlyPlayingUri == uiState.customSoundUri,
                            onClick = { /* Already selected */ },
                            onPlay = { onPreviewSound(uiState.customSoundUri) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    // Preset sounds
                    AlarmSound.entries.forEach { sound ->
                        val isSelected = uiState.customSoundUri == null && uiState.alarmSound == sound
                        AlarmSoundOption(
                            name = sound.displayName,
                            isSelected = isSelected,
                            isPlaying = uiState.currentlyPlayingUri == sound.uri,
                            onClick = { onSelectAlarmSound(sound) },
                            onPlay = { onPreviewSound(sound.uri) }
                        )
                        if (sound != AlarmSound.entries.last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // === DEVICE RINGTONES - More Sounds ===
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, MegadriveOrange, RoundedCornerShape(4.dp)),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAllRingtones = !showAllRingtones },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = ">> MORE SOUNDS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MegadriveOrange
                            )
                            Text(
                                text = "${uiState.deviceRingtones.size} device ringtones available",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (showAllRingtones) "[-]" else "[+]",
                            style = MaterialTheme.typography.titleMedium,
                            color = MegadriveOrange
                        )
                    }

                    if (showAllRingtones && uiState.deviceRingtones.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        uiState.deviceRingtones.take(50).forEach { ringtone ->
                            val isSelected = uiState.customSoundUri == ringtone.uri
                            AlarmSoundOption(
                                name = ringtone.name,
                                isSelected = isSelected,
                                isPlaying = uiState.currentlyPlayingUri == ringtone.uri,
                                onClick = { onSelectCustomSound(ringtone.name, ringtone.uri) },
                                onPlay = { onPreviewSound(ringtone.uri) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        }
                        if (uiState.deviceRingtones.size > 50) {
                            Text(
                                text = "... and ${uiState.deviceRingtones.size - 50} more",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            // === VIBRATION - Rumble Pack ===
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, MegadriveCyan, RoundedCornerShape(4.dp)),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ">> RUMBLE PAK",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MegadriveCyan
                        )
                        Text(
                            text = "Enable controller vibration",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.vibrationEnabled,
                        onCheckedChange = onToggleVibration,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MegadriveGreen,
                            checkedTrackColor = MegadriveGreen.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            // === PERMISSIONS - System access ===
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, MegadriveGreen, RoundedCornerShape(4.dp))
                    .clickable { onNavigateToPermissions() },
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ">> PERMISSIONS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MegadriveGreen
                        )
                        Text(
                            text = "Manage system access",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "[>]",
                        style = MaterialTheme.typography.titleMedium,
                        color = MegadriveGreen
                    )
                }
            }

            // === INFO CARD - Pro Tips ===
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, MegadriveGold, RoundedCornerShape(4.dp)),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "* PRO TIP *",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MegadriveGold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sound and vibration changes take effect for new notifications. Restart the service to apply changes to active quests.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === BACK BUTTON ===
        FeedbackButton(
            text = "< BACK TO GAME",
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            color = MegadriveCyan,
            sound = ButtonSound.BACK
        )
    }
}

@Composable
private fun AlarmSoundOption(
    name: String,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    val rowInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val rowPressed by rowInteraction.collectIsPressedAsState()

    // Track "just clicked" state for flash effect
    var justClicked by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(justClicked) {
        if (justClicked) {
            kotlinx.coroutines.delay(150)
            justClicked = false
        }
    }

    val isHighlighted = rowPressed || justClicked

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = {
                    justClicked = true
                    onClick()
                },
                interactionSource = rowInteraction,
                indication = androidx.compose.foundation.LocalIndication.current
            )
            .background(if (isHighlighted) MegadriveGreen.copy(alpha = 0.3f) else Color.Transparent)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isSelected || isHighlighted) "> $name" else "  $name",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected || isHighlighted) MegadriveGreen else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected || isHighlighted) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Text(
                    text = "*",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MegadriveGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        FeedbackOutlinedButton(
            onClick = onPlay,
            color = if (isPlaying) MegadriveGreen else MegadriveCyan,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isPlaying) "..." else "PLAY",
                fontSize = 10.sp
            )
        }
    }
}
