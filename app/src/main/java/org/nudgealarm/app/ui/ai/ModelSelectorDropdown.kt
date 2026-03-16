package org.nudgealarm.app.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nudgealarm.app.ai.model.ModelCatalog
import org.nudgealarm.app.ai.model.ModelInfo
import org.nudgealarm.app.ai.model.ModelManager

@Composable
fun ModelSelectorDropdown(
    selectedModelId: String?,
    installedModels: List<ModelInfo>,
    onSelectModel: (String) -> Unit,
    onManageModels: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedName = selectedModelId?.let { id ->
        ModelCatalog.findById(id)?.name
    } ?: if (installedModels.isEmpty()) "No models" else "Select model"

    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.widthIn(max = 140.dp)
        ) {
            Text(
                text = selectedName,
                color = Color(0xFF90CAF9),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Color(0xFF90CAF9)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            installedModels.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = model.name,
                            color = if (model.id == selectedModelId) Color(0xFF90CAF9) else Color.Unspecified
                        )
                    },
                    onClick = {
                        onSelectModel(model.id)
                        expanded = false
                    }
                )
            }

            DropdownMenuItem(
                text = { Text("Manage Models...") },
                onClick = {
                    expanded = false
                    onManageModels()
                }
            )
        }
    }
}
