package se.joynes.nudgealarm.ui

import android.widget.NumberPicker
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.OutlinedTextField
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
import se.joynes.nudgealarm.storage.SnoozeClockShortcut
import se.joynes.nudgealarm.storage.nextSnoozeClockTime
import se.joynes.nudgealarm.storage.snoozeClockMinutes
import se.joynes.nudgealarm.ui.theme.MegadriveCyan
import java.text.DateFormat
import java.util.Date
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

private fun clockLabel(hour: Int, minute: Int) = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

private fun clockDayLabel(hour: Int, minute: Int, now: Long): String {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val targetDay = Instant.ofEpochMilli(nextSnoozeClockTime(hour, minute, now)).atZone(zone).toLocalDate()
    return if (targetDay == today) "Today" else "Tomorrow"
}

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
    previewPrefix: String = "Remind me in",
    nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    val context = LocalContext.current
    val preferences = remember(context) { SnoozePreferences(context) }
    val history = remember(preferences) { preferences.read() }
    var hours by remember { mutableIntStateOf(history.lastMinutes / 60) }
    var minutes by remember { mutableIntStateOf(history.lastMinutes % 60) }
    var showRecent by remember { mutableStateOf(false) }
    var customDurationSelected by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var clockMode by remember { mutableStateOf(false) }
    var shortcuts by remember { mutableStateOf(preferences.clockShortcuts()) }
    var showShortcutEditor by remember { mutableStateOf(false) }
    var editingShortcut by remember { mutableStateOf<SnoozeClockShortcut?>(null) }
    val initialClock = remember { Instant.ofEpochMilli(nowMillis()).atZone(ZoneId.systemDefault()).plusHours(1) }
    var clockHour by remember { mutableIntStateOf(initialClock.hour) }
    var clockMinute by remember { mutableIntStateOf(0) }
    val applyDuration: (Int) -> Unit = { choice ->
        if (!submitted && choice > 0) {
            submitted = true
            preferences.record(choice)
            onSnooze(choice)
        }
    }
    val applyClockTime: (Int, Int) -> Unit = { hour, minute ->
        if (!submitted) {
            submitted = true
            // Fixed clock times are not relative-duration history entries.
            onSnooze(snoozeClockMinutes(hour, minute, nowMillis()))
        }
    }
    val duration = hours * 60 + minutes
    val resumeTime = remember(duration) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(System.currentTimeMillis() + duration * 60_000L))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, color = MegadriveCyan) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description, color = MaterialTheme.colorScheme.onSurface)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { clockMode = false }, modifier = Modifier.weight(1f)) {
                        Text(if (!clockMode) "● DURATION" else "DURATION", color = MegadriveCyan)
                    }
                    TextButton(onClick = { clockMode = true }, modifier = Modifier.weight(1f).testTag("snooze_clock_mode")) {
                        Text(if (clockMode) "● UNTIL TIME" else "UNTIL TIME", color = MegadriveCyan)
                    }
                }
                if (clockMode) {
                    Text("SAVED TIMES", style = MaterialTheme.typography.labelSmall)
                    Text("Choose the next occurrence. Passed times apply tomorrow.", style = MaterialTheme.typography.bodySmall)
                    if (shortcuts.isEmpty()) Text("Save times such as Lunch or After work.")
                    shortcuts.sortedBy { nextSnoozeClockTime(it.hour, it.minute, nowMillis()) }.forEach { shortcut ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { applyClockTime(shortcut.hour, shortcut.minute) },
                                enabled = !submitted,
                                modifier = Modifier.weight(1f).testTag("clock_shortcut_${shortcut.id}")
                            ) {
                                Column {
                                    Text(shortcut.name, color = MegadriveCyan)
                                    Text("${clockLabel(shortcut.hour, shortcut.minute)} · ${clockDayLabel(shortcut.hour, shortcut.minute, nowMillis())}")
                                }
                            }
                            TextButton(onClick = { editingShortcut = shortcut; showShortcutEditor = true },
                                modifier = Modifier.testTag("edit_clock_shortcut_${shortcut.id}")) {
                                Text("EDIT", color = MegadriveCyan)
                            }
                        }
                    }
                    TextButton(onClick = { editingShortcut = null; showShortcutEditor = true }) {
                        Text("+ ADD SHORTCUT", color = MegadriveCyan)
                    }
                    Text("OR CHOOSE A TIME", style = MaterialTheme.typography.labelSmall)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically) {
                        DurationWheel("HOUR", clockHour, 23, "snooze_clock_hours_wheel") { clockHour = it }
                        Text(":", color = MegadriveCyan, fontSize = 28.sp)
                        DurationWheel("MINUTE", clockMinute, 59, "snooze_clock_minutes_wheel") { clockMinute = it }
                    }
                    Text("Until ${clockLabel(clockHour, clockMinute)} · ${clockDayLabel(clockHour, clockMinute, nowMillis())}", color = MegadriveCyan)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DurationWheel("HOURS", hours, MAX_SNOOZE_HOURS, "snooze_hours_wheel") { hours = it; customDurationSelected = true }
                        Text(":", color = MegadriveCyan, fontSize = 28.sp)
                        DurationWheel("MINUTES", minutes, 59, "snooze_minutes_wheel") { minutes = it; customDurationSelected = true }
                    }
                    Text(
                        if (duration > 0) "$previewPrefix ${snoozeDurationLabel(duration)} · $resumeTime"
                        else "Choose at least 1 minute",
                        color = if (duration > 0) MegadriveCyan else MaterialTheme.colorScheme.error
                    )
                    Text("QUICK PICKS", style = MaterialTheme.typography.labelSmall)
                    history.suggestions.chunked(3).forEach { choices ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            choices.forEach { choice ->
                                OutlinedButton(
                                    onClick = { applyDuration(choice) },
                                    enabled = !submitted,
                                    modifier = Modifier.weight(1f).testTag("quick_pick_$choice"),
                                    border = BorderStroke(1.dp, MegadriveCyan),
                                    contentPadding = PaddingValues(4.dp)
                                ) { Text(snoozeDurationLabel(choice), color = MegadriveCyan) }
                            }
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
                                            showRecent = false
                                            applyDuration(choice)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (clockMode) Button(
                onClick = { applyClockTime(clockHour, clockMinute) },
                enabled = !submitted,
                colors = ButtonDefaults.buttonColors(containerColor = MegadriveCyan)
            ) { Text("$confirmLabel UNTIL ${clockLabel(clockHour, clockMinute)}") }
            else if (customDurationSelected) Button(
                onClick = { applyDuration(duration) },
                enabled = duration > 0 && !submitted,
                colors = ButtonDefaults.buttonColors(containerColor = MegadriveCyan)
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = MegadriveCyan) } }
    )
    if (showShortcutEditor) ClockShortcutEditor(
        shortcut = editingShortcut,
        initialHour = clockHour,
        initialMinute = clockMinute,
        onSave = { name, hour, minute ->
            preferences.saveClockShortcut(editingShortcut?.id, name, hour, minute)
            shortcuts = preferences.clockShortcuts()
            showShortcutEditor = false
        },
        onDelete = {
            editingShortcut?.let { preferences.deleteClockShortcut(it.id) }
            shortcuts = preferences.clockShortcuts()
            showShortcutEditor = false
        },
        onDismiss = { showShortcutEditor = false }
    )
}

@Composable
private fun ClockShortcutEditor(
    shortcut: SnoozeClockShortcut?, initialHour: Int, initialMinute: Int,
    onSave: (String, Int, Int) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(shortcut?.name ?: "") }
    var hour by remember { mutableIntStateOf(shortcut?.hour ?: initialHour) }
    var minute by remember { mutableIntStateOf(shortcut?.minute ?: initialMinute) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (shortcut == null) "ADD TIME SHORTCUT" else "EDIT TIME SHORTCUT", color = MegadriveCyan) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(60) },
                    label = { Text("Name, e.g. Lunch or After work") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("clock_shortcut_name"))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically) {
                    DurationWheel("HOUR", hour, 23, "shortcut_hours_wheel") { hour = it }
                    Text(":", color = MegadriveCyan, fontSize = 28.sp)
                    DurationWheel("MINUTE", minute, 59, "shortcut_minutes_wheel") { minute = it }
                }
                Text("${clockLabel(hour, minute)} every day", color = MegadriveCyan)
                if (shortcut != null) TextButton(onClick = onDelete) { Text("DELETE SHORTCUT", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), hour, minute) }, enabled = name.isNotBlank()) { Text("SAVE SHORTCUT") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
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
                    wrapSelectorWheel = maximum == 59 || maximum == 23
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
