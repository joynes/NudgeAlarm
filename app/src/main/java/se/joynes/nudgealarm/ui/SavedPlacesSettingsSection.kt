package se.joynes.nudgealarm.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import se.joynes.nudgealarm.database.SavedPlaceLocationEntity
import se.joynes.nudgealarm.database.SavedPlaceWithLocations
import se.joynes.nudgealarm.ui.theme.MegadriveCyan
import se.joynes.nudgealarm.ui.theme.MegadriveGreen
import se.joynes.nudgealarm.ui.theme.MegadriveRed

@Composable
internal fun SavedPlacesSettingsSection(
    places: List<SavedPlaceWithLocations>,
    linkedReminderCounts: Map<String, Int>,
    error: String?,
    onRenamePlace: (String, String) -> Unit,
    onUpdatePosition: (String, String, String, Double, Double, Int) -> Unit,
    onSelectPosition: (String, String) -> Unit,
    onDeletePosition: (String, String) -> Unit,
    onDeletePlace: (String) -> Unit
) {
    var renaming by remember { mutableStateOf<SavedPlaceWithLocations?>(null) }
    var editing by remember { mutableStateOf<Pair<String, SavedPlaceLocationEntity>?>(null) }
    var deletingPlace by remember { mutableStateOf<SavedPlaceWithLocations?>(null) }
    var deletingPosition by remember { mutableStateOf<Pair<String, SavedPlaceLocationEntity>?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth().border(2.dp, MegadriveCyan, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(">> SAVED PLACES", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MegadriveCyan)
            Text("Manage places used by your quests", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (places.isEmpty()) {
                Text("No saved places yet. Create one while editing a quest.", style = MaterialTheme.typography.bodySmall)
            }
            places.forEach { saved ->
                HorizontalDivider()
                Text(saved.place.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MegadriveGreen)
                val count = linkedReminderCounts[saved.place.id] ?: 0
                Text("$count linked quest${if (count == 1) "" else "s"} · ${saved.locations.size} saved position${if (saved.locations.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(modifier = Modifier.testTag("rename-place-${saved.place.id}"), onClick = { renaming = saved }) {
                        Text("RENAME", color = MegadriveCyan)
                    }
                    TextButton(modifier = Modifier.testTag("delete-place-${saved.place.id}"), onClick = { deletingPlace = saved }) {
                        Text("DELETE PLACE", color = MegadriveRed)
                    }
                }
                saved.locations.forEach { position ->
                    val active = position.id == saved.place.activeLocationId
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 8.dp)) {
                        Text("${position.label}${if (active) " · ACTIVE" else ""}", color = if (active) MegadriveGreen else MaterialTheme.colorScheme.onSurface)
                        Text("${position.latitude}, ${position.longitude} · ${position.radiusMeters} m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!active) {
                                TextButton(modifier = Modifier.testTag("use-position-${position.id}"), onClick = { onSelectPosition(saved.place.id, position.id) }) {
                                    Text("USE", color = MegadriveGreen)
                                }
                            }
                            TextButton(modifier = Modifier.testTag("edit-position-${position.id}"), onClick = { editing = saved.place.id to position }) {
                                Text("EDIT", color = MegadriveCyan)
                            }
                            TextButton(
                                modifier = Modifier.testTag("delete-position-${position.id}"),
                                onClick = { deletingPosition = saved.place.id to position },
                                enabled = saved.locations.size > 1
                            ) {
                                Text("DELETE", color = MegadriveRed)
                            }
                        }
                    }
                }
                if (saved.locations.size == 1) {
                    Text("Delete the place to remove its last position.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            error?.let { Text(it, color = MegadriveRed, style = MaterialTheme.typography.bodySmall) }
        }
    }

    renaming?.let { saved ->
        var name by remember(saved.place.id) { mutableStateOf(saved.place.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename place") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Place name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { onRenamePlace(saved.place.id, name); renaming = null }, enabled = name.isNotBlank()) {
                    Text("SAVE")
                }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("CANCEL") } }
        )
    }

    editing?.let { (placeId, position) ->
        var label by remember(position.id) { mutableStateOf(position.label) }
        var latitude by remember(position.id) { mutableStateOf(position.latitude.toString()) }
        var longitude by remember(position.id) { mutableStateOf(position.longitude.toString()) }
        var radius by remember(position.id) { mutableStateOf(position.radiusMeters.toString()) }
        val lat = latitude.toDoubleOrNull()
        val lon = longitude.toDoubleOrNull()
        val meters = radius.toIntOrNull()
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit saved position") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Position label") }, singleLine = true)
                    OutlinedTextField(value = latitude, onValueChange = { latitude = it }, label = { Text("Latitude") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = longitude, onValueChange = { longitude = it }, label = { Text("Longitude") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = radius, onValueChange = { radius = it }, label = { Text("Radius in meters") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdatePosition(placeId, position.id, label, lat!!, lon!!, meters!!)
                        editing = null
                    },
                    enabled = label.isNotBlank() && lat != null && lat in -90.0..90.0 && lon != null && lon in -180.0..180.0 && meters != null && meters in 1..100_000
                ) { Text("SAVE") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("CANCEL") } }
        )
    }

    deletingPosition?.let { (placeId, position) ->
        AlertDialog(
            onDismissRequest = { deletingPosition = null },
            title = { Text("Delete saved position?") },
            text = { Text("${position.label} will be removed. If it is active, another saved position becomes active.") },
            confirmButton = {
                TextButton(onClick = { onDeletePosition(placeId, position.id); deletingPosition = null }) { Text("DELETE", color = MegadriveRed) }
            },
            dismissButton = { TextButton(onClick = { deletingPosition = null }) { Text("CANCEL") } }
        )
    }

    deletingPlace?.let { saved ->
        val count = linkedReminderCounts[saved.place.id] ?: 0
        AlertDialog(
            onDismissRequest = { deletingPlace = null },
            title = { Text("Delete ${saved.place.name}?") },
            text = { Text("This removes all ${saved.locations.size} saved positions. $count linked quest${if (count == 1) "" else "s"} will change to ANYWHERE.") },
            confirmButton = {
                TextButton(modifier = Modifier.testTag("confirm-delete-place"), onClick = { onDeletePlace(saved.place.id); deletingPlace = null }) {
                    Text("DELETE PLACE", color = MegadriveRed)
                }
            },
            dismissButton = { TextButton(onClick = { deletingPlace = null }) { Text("CANCEL") } }
        )
    }
}
