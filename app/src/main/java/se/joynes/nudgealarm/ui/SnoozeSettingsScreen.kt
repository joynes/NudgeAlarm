package se.joynes.nudgealarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import se.joynes.nudgealarm.storage.*
import se.joynes.nudgealarm.ui.theme.MegadriveCyan
import kotlin.math.roundToInt

@Composable
internal fun SnoozeSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember(context) { SnoozePreferences(context) }
    var config by remember { mutableStateOf(prefs.configuration()) }
    var clocks by remember { mutableStateOf(prefs.clockShortcuts()) }
    var durationEditor by remember { mutableStateOf(false) }
    var defaultEditor by remember { mutableStateOf(false) }
    var oldDuration by remember { mutableStateOf<Int?>(null) }
    var clockEditor by remember { mutableStateOf(false) }
    var oldClock by remember { mutableStateOf<SnoozeClockShortcut?>(null) }
    var status by remember { mutableStateOf("") }
    val save: (SnoozeConfiguration) -> Unit = { prefs.saveConfiguration(it); config = prefs.configuration() }
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("SNOOZE TIMES", style = MaterialTheme.typography.headlineMedium, color = MegadriveCyan)
        Text("Shared by quest snooze and pause-all. Changes do not alter an already active pause.", style = MaterialTheme.typography.bodySmall)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("DURATION PRESETS", style = MaterialTheme.typography.titleMedium)
            Text("Shortest first. Turn adaptive suggestions off to show only your configured presets.")
            config.durationPresets.sorted().forEach { minutes ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(snoozeDurationLabel(minutes), Modifier.weight(1f))
                    TextButton(onClick = { oldDuration = minutes; defaultEditor = false; durationEditor = true }, modifier = Modifier.testTag("edit-duration-$minutes")) { Text("EDIT") }
                    TextButton(onClick = { save(config.copy(durationPresets = config.durationPresets - minutes)) }, modifier = Modifier.testTag("delete-duration-$minutes")) { Text("DELETE") }
                }
            }
            TextButton(onClick = { oldDuration = null; defaultEditor = false; durationEditor = true }, enabled = config.durationPresets.size < 48) { Text("+ ADD DURATION") }
            TextButton(onClick = { save(config.copy(durationPresets = DEFAULT_SNOOZE_PRESETS)) }) { Text("RESTORE DEFAULT PRESETS") }
            SettingsToggle("Adaptive suggestions", "Learn from duration choices. Clock shortcuts are never learned as durations.", config.adaptive, "snooze-adaptive") { save(config.copy(adaptive = it)) }
            Text("Maximum Quick Picks: ${config.quickPickCount}")
            Slider(value = config.quickPickCount.toFloat(), onValueChange = { save(config.copy(quickPickCount = it.roundToInt())) }, valueRange = 1f..24f, steps = 22, modifier = Modifier.testTag("snooze-quick-count"))
            Text("Recent choices: ${config.recentCount}${if (config.recentCount == 0) " (hidden)" else ""}")
            Slider(value = config.recentCount.toFloat(), onValueChange = { save(config.copy(recentCount = it.roundToInt())) }, valueRange = 0f..20f, steps = 19, modifier = Modifier.testTag("snooze-recent-count"))
            SettingsToggle("Remember last duration", "Otherwise the wheels start at your default duration.", config.rememberLast, "snooze-remember-last") { save(config.copy(rememberLast = it)) }
            OutlinedButton(onClick = { oldDuration = config.defaultMinutes; defaultEditor = true; durationEditor = true }) { Text("DEFAULT DURATION: ${snoozeDurationLabel(config.defaultMinutes)}") }
            TextButton(onClick = { prefs.clearHistory(); status = "Duration history cleared; saved presets and clock times are kept." }) { Text("CLEAR DURATION HISTORY") }
            HorizontalDivider()
            Text("CLOCK SHORTCUTS", style = MaterialTheme.typography.titleMedium)
            Text("Daily local clock times, used only when you tap them. Passed times apply tomorrow.")
            clocks.sortedWith(compareBy<SnoozeClockShortcut> { it.hour }.thenBy { it.minute }).forEach { shortcut ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${shortcut.name} · %02d:%02d".format(shortcut.hour, shortcut.minute), Modifier.weight(1f))
                    TextButton(onClick = { oldClock = shortcut; clockEditor = true }, modifier = Modifier.testTag("settings-edit-clock-${shortcut.id}")) { Text("EDIT") }
                }
            }
            TextButton(onClick = { oldClock = null; clockEditor = true }, enabled = clocks.size < 48) { Text("+ ADD CLOCK SHORTCUT") }
            Text("Initially open")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("duration" to "DURATION", "clock" to "UNTIL TIME").forEach { (mode, label) ->
                    OutlinedButton(onClick = { save(config.copy(defaultMode = mode)) }, modifier = Modifier.testTag("default-mode-$mode")) {
                        Text("${if (config.defaultMode == mode) "● " else ""}$label")
                    }
                }
            }
            if (status.isNotEmpty()) Text(status, color = MegadriveCyan)
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("BACK TO OPTIONS") }
    }
    if (durationEditor) DurationPresetEditor(
        title = if (defaultEditor) "DEFAULT DURATION" else if (oldDuration == null) "ADD DURATION" else "EDIT DURATION",
        initial = oldDuration ?: config.defaultMinutes,
        onSave = { minutes ->
            if (defaultEditor) save(config.copy(defaultMinutes = minutes))
            else save(config.copy(durationPresets = ((config.durationPresets - listOfNotNull(oldDuration)) + minutes).distinct().sorted()))
            durationEditor = false
        }, onDismiss = { durationEditor = false }
    )
    if (clockEditor) ClockShortcutEditor(oldClock, 12, 0,
        onSave = { name, hour, minute -> prefs.saveClockShortcut(oldClock?.id, name, hour, minute); clocks = prefs.clockShortcuts(); clockEditor = false },
        onDelete = { oldClock?.let { prefs.deleteClockShortcut(it.id) }; clocks = prefs.clockShortcuts(); clockEditor = false },
        onDismiss = { clockEditor = false })
}

@Composable
private fun SettingsToggle(title: String, description: String, enabled: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); Text(description, style = MaterialTheme.typography.bodySmall) }
        Switch(checked = enabled, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun DurationPresetEditor(title: String, initial: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var hours by remember { mutableIntStateOf(initial / 60) }
    var minutes by remember { mutableIntStateOf(initial % 60) }
    val duration = hours * 60 + minutes
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DurationWheel("HOURS", hours, MAX_SNOOZE_HOURS, "preset_hours_wheel") { hours = it }
                DurationWheel("MINUTES", minutes, 59, "preset_minutes_wheel") { minutes = it }
            }
            Text(if (duration > 0) snoozeDurationLabel(duration) else "Choose at least 1 minute")
        }
    }, confirmButton = { TextButton(onClick = { onSave(duration) }, enabled = duration > 0) { Text("SAVE DURATION") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } })
}
