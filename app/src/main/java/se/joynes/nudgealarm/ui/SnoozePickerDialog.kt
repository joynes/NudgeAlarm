package se.joynes.nudgealarm.ui

import android.widget.NumberPicker
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import se.joynes.nudgealarm.storage.MAX_SNOOZE_HOURS
import se.joynes.nudgealarm.storage.SnoozePreferences
import se.joynes.nudgealarm.ui.theme.MegadriveCyan
import java.text.DateFormat
import java.util.Date

internal fun snoozeDurationLabel(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

@Composable
internal fun SnoozePickerDialog(
    title: String,
    description: String,
    onSnooze: (Int) -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "SNOOZE",
    previewPrefix: String = "Remind me in"
) {
    val context = LocalContext.current
    val preferences = remember(context) { SnoozePreferences(context) }
    val history = remember(preferences) { preferences.read() }
    var hours by remember { mutableIntStateOf(history.lastMinutes / 60) }
    var minutes by remember { mutableIntStateOf(history.lastMinutes % 60) }
    var showRecent by remember { mutableStateOf(false) }
    val duration = hours * 60 + minutes
    val resumeTime = remember(duration) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(System.currentTimeMillis() + duration * 60_000L))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, color = MegadriveCyan) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description, color = MaterialTheme.colorScheme.onSurface)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DurationWheel("HOURS", hours, MAX_SNOOZE_HOURS, "snooze_hours_wheel") { hours = it }
                    Text(":", color = MegadriveCyan, fontSize = 28.sp)
                    DurationWheel("MINUTES", minutes, 59, "snooze_minutes_wheel") { minutes = it }
                }
                Text(
                    if (duration > 0) "$previewPrefix ${snoozeDurationLabel(duration)} · $resumeTime"
                    else "Choose at least 1 minute",
                    color = if (duration > 0) MegadriveCyan else MaterialTheme.colorScheme.error
                )
                Text("QUICK PICKS", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    history.suggestions.forEach { choice ->
                        OutlinedButton(
                            onClick = { hours = choice / 60; minutes = choice % 60 },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, MegadriveCyan),
                            contentPadding = PaddingValues(4.dp)
                        ) { Text(snoozeDurationLabel(choice), color = MegadriveCyan) }
                    }
                }
                if (history.recent.isNotEmpty()) {
                    Box {
                        TextButton(onClick = { showRecent = true }) {
                            Text("RECENT ▾", color = MegadriveCyan)
                        }
                        DropdownMenu(expanded = showRecent, onDismissRequest = { showRecent = false }) {
                            history.recent.forEach { choice ->
                                DropdownMenuItem(
                                    text = { Text(snoozeDurationLabel(choice)) },
                                    onClick = {
                                        hours = choice / 60
                                        minutes = choice % 60
                                        showRecent = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { preferences.record(duration); onSnooze(duration) },
                enabled = duration > 0,
                colors = ButtonDefaults.buttonColors(containerColor = MegadriveCyan)
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = MegadriveCyan) } }
    )
}

@Composable
private fun DurationWheel(label: String, value: Int, maximum: Int, tag: String, onChange: (Int) -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val textSize = with(LocalDensity.current) { 28.sp.toPx() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MegadriveCyan)
        AndroidView(
            modifier = Modifier.width(96.dp).height(144.dp).testTag(tag),
            factory = { context ->
                NumberPicker(context).apply {
                    minValue = 0
                    maxValue = maximum
                    wrapSelectorWheel = maximum == 59
                    descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                    contentDescription = label.lowercase()
                    setOnValueChangedListener { _, _, new -> onChange(new) }
                }
            },
            update = { picker ->
                picker.textColor = color
                picker.textSize = textSize
                if (picker.value != value) picker.value = value
            }
        )
    }
}
