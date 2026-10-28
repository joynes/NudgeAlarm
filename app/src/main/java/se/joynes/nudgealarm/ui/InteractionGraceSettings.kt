package se.joynes.nudgealarm.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import se.joynes.nudgealarm.notification.PhoneUseMonitor

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InteractionGraceSettings(minutes: Int, onChange: (Int) -> Unit,
                                      source: String = "unlock", onSourceChange: (String) -> Unit = {}) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var usageAccess by remember { mutableStateOf(PhoneUseMonitor.hasUsageAccess(context)) }
    var disclosure by remember { mutableStateOf(false) }
    var settingsError by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) usageAccess = PhoneUseMonitor.hasUsageAccess(context)
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    var custom by remember { mutableStateOf(false) }
    var hours by remember { mutableIntStateOf(0) }
    var remainder by remember { mutableIntStateOf(15) }
    Card(Modifier.fillMaxWidth().testTag("interaction-grace-settings")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AFTER USING THE PHONE", style = MaterialTheme.typography.titleMedium)
            Text("Phone use quiets reminders already delivered. A new reminder alerts until you use the phone again. Reminders stay visible.",
                style = MaterialTheme.typography.bodySmall)
            Text("What counts as using the phone?", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("app" to "NudgeAlarm only", "unlock" to "Unlock phone").forEach { (value, label) ->
                    FilterChip(selected = source == value, onClick = { onSourceChange(value) },
                        modifier = Modifier.testTag("grace-source-$value"), label = { Text(label) })
                }
                FilterChip(selected = source == "usage", onClick = {
                    if (usageAccess) onSourceChange("usage") else disclosure = true
                }, modifier = Modifier.testTag("grace-source-usage"), label = { Text("Other apps too") })
            }
            Text(when (source) {
                "app" -> "Only opening or interacting with NudgeAlarm starts the break."
                "usage" -> "Unlocking and Android-reported interaction in other apps count. Usage is checked before each nag; Android may not report every touch."
                else -> "Unlocking the phone or using NudgeAlarm starts the break. Simply lighting the screen does not."
            }, style = MaterialTheme.typography.bodySmall)
            if (source == "usage" && !usageAccess) {
                Text("Usage access is off. Unlock detection still works.", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { disclosure = true }) { Text("Enable usage access") }
            }
            if (settingsError) Text("Could not open Android Usage access. Find NudgeAlarm under Special app access in Android Settings.", color = MaterialTheme.colorScheme.error)
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
            Text(if (minutes == 0) "No automatic break" else "Delivered reminders stay quiet for ${snoozeDurationLabel(minutes)} after your last detected use. Default: 15m.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
    if (custom) AlertDialog(onDismissRequest = { custom = false },
        title = { Text("Break after phone use") },
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
    if (disclosure) AlertDialog(onDismissRequest = { disclosure = false },
        title = { Text("Detect use in other apps") },
        text = { Text("Android Usage access can expose which apps you use and when. NudgeAlarm uses only the times of unlocks and user-interaction events to quiet reminders already delivered. It does not read screen contents, store app names, or upload usage history. This is optional; unlocking works without it. You can revoke access in Android Settings.") },
        confirmButton = { TextButton(onClick = {
            disclosure = false
            onSourceChange("usage")
            settingsError = runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .setData(android.net.Uri.parse("package:${context.packageName}"))) }.isFailure
        }, modifier = Modifier.testTag("open-usage-access")) { Text("Open usage access") } },
        dismissButton = { TextButton(onClick = { disclosure = false }) { Text("Cancel") } })
}
