package se.joynes.nudgealarm.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InteractionGraceSettings(minutes: Int, onChange: (Int) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    var hours by remember { mutableIntStateOf(0) }
    var remainder by remember { mutableIntStateOf(15) }
    Card(Modifier.fillMaxWidth().testTag("interaction-grace-settings")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AFTER USING THE APP", style = MaterialTheme.typography.titleMedium)
            Text("Give repeat alerts a break after opening or interacting with NudgeAlarm. Reminders stay visible.",
                style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 5, 15, 30, 60).forEach { choice ->
                    FilterChip(selected = minutes == choice, onClick = { onChange(choice) },
                        modifier = Modifier.testTag("interaction-grace-$choice"),
                        label = { Text(if (choice == 0) "Off" else snoozeDurationLabel(choice)) })
                }
                FilterChip(selected = minutes !in listOf(0, 5, 15, 30, 60), onClick = {
                    hours = minutes / 60; remainder = minutes % 60; custom = true
                },
                    label = { Text("Custom…") })
            }
            Text(if (minutes == 0) "No automatic break" else "Repeat alerts wait ${snoozeDurationLabel(minutes)} after your last interaction. Default: 15m.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
    if (custom) AlertDialog(onDismissRequest = { custom = false },
        title = { Text("Break after using app") },
        text = {
            Column {
                Text("How long should repeat alerts wait after your last interaction?")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    DurationWheel("HOURS", hours, 168, "grace-hours") { hours = it }
                    DurationWheel("MINUTES", remainder, 59, "grace-minutes") { remainder = it }
                }
            }
        },
        confirmButton = { TextButton(onClick = { custom = false; onChange(hours * 60 + remainder) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { custom = false }) { Text("Cancel") } })
}
