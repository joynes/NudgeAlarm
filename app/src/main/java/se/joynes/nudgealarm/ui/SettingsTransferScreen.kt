package se.joynes.nudgealarm.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.joynes.nudgealarm.storage.*
import se.joynes.nudgealarm.ui.theme.MegadriveCyan

@Composable
internal fun SettingsTransferScreen(onBack: () -> Unit, onImported: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember(context) { SettingsTransferStore(context) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<SettingsConfiguration?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) { runCatching { store.exportToUri(uri) } }
            status = result.fold({ "Settings exported. No file was uploaded automatically." }, { "Export failed: ${it.message}" })
            busy = false
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) { runCatching { store.readFromUri(uri) } }
            result.fold({ pending = it; status = "" }, { status = "Import rejected; nothing changed: ${it.message}" })
            busy = false
        }
    }
    BackHandler { if (!busy) onBack() }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SETTINGS IMPORT / EXPORT", style = MaterialTheme.typography.headlineSmall, color = MegadriveCyan)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("An editable, documented JSON file for discussing your settings with an AI.")
            Text("Each setting includes its value, default, detailed description, constraints, example and a discussion field. Edit value to change behavior; discussion notes are preserved. Other documentation never changes app behavior.")
            Text("Includes notification preferences and both snooze variations. Does NOT include quests, places/coordinates, logs, permissions, active pauses or learned history. Use the separate full-data backup for quests and places.")
            Text("Shortcut names, discussion notes and a device-local sound URI may be personal. Review the file before sharing. Nothing is uploaded automatically.")
            OutlinedButton(onClick = { exporter.launch("nudgealarm_settings.json") }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("export-settings")) { Text("EXPORT SETTINGS") }
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("import-settings")) { Text("IMPORT SETTINGS") }
            Text("Import validates the whole file, then asks you to review changes. Only configurable settings are replaced; running pause timers and quest/place data are kept.")
            if (busy) CircularProgressIndicator()
            if (status.isNotBlank()) Text(status, modifier = Modifier.testTag("settings-transfer-status"))
        }
        TextButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("BACK TO OPTIONS") }
    }
    pending?.let { configuration ->
        SettingsImportPreview(configuration, store.snapshot(), busy, onCancel = { if (!busy) pending = null }, onApply = {
            scope.launch {
                busy = true
                val result = withContext(Dispatchers.IO) { runCatching { store.apply(configuration) } }
                result.fold({ pending = null; onImported(); status = "Settings imported. Quests, places and active pauses were kept." }, { status = "Import failed: ${it.message}" })
                busy = false
            }
        })
    }
}

@Composable
internal fun SettingsImportPreview(incoming: SettingsConfiguration, current: SettingsConfiguration, busy: Boolean = false, onCancel: () -> Unit, onApply: () -> Unit) {
    val changes = SettingsConfigurationCodec.changes(current, incoming)
    AlertDialog(onDismissRequest = { if (!busy) onCancel() }, title = { Text("REVIEW SETTINGS IMPORT") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${changes.size} changed settings. Discussion notes are also imported. Nothing is applied until you confirm.")
            changes.forEach { Text(it) }
            Text("Quests, saved places, permissions, learned history and active pause timers will not change. Sound changes recreate the notification channel; shorter retention can expire non-sticky quests on the next scheduler evaluation.")
        }
    }, confirmButton = { TextButton(onClick = onApply, enabled = !busy, modifier = Modifier.testTag("confirm-settings-import")) { Text("APPLY SETTINGS") } },
        dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text("CANCEL") } })
}
