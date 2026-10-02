package se.joynes.nudgealarm.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import se.joynes.nudgealarm.location.GeofenceStateStore

@Composable
internal fun GeofenceSetupCard(onPermissions: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { GeofenceStateStore(context) }
    var status by remember { mutableStateOf(store.status()) }
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("geofence_state", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> status = store.status() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("geofence-setup")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PLACE-ONLY REMINDERS", style = MaterialTheme.typography.titleSmall)
            Text("1. Edit a quest and choose or create a place, such as Home.\n" +
                "2. Save your current position. A radius of 200–250 m is a good starting point.\n" +
                "3. In Android permissions, turn on precise Location and Allow all the time. Keep Android Location on.\n" +
                "4. Save the quest and keep reminders running.", style = MaterialTheme.typography.bodySmall)
            Text("Existing places work automatically. Android detects entry and exit; all linked quests reuse one saved place status, without repeated GPS requests. Updates can take a few minutes. Unknown location keeps alerts paused.", style = MaterialTheme.typography.bodySmall)
            Text(status, modifier = Modifier.testTag("geofence-status"), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onPermissions, modifier = Modifier.testTag("geofence-permissions")) { Text("CHECK PERMISSIONS") }
        }
    }
}
