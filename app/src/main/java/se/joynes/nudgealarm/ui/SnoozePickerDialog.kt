package se.joynes.nudgealarm.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import se.joynes.nudgealarm.ui.theme.MegadriveCyan
import java.util.Calendar

internal fun minutesUntilSnooze(targetMillis: Long, nowMillis: Long): Int? {
    val difference = targetMillis - nowMillis
    if (difference <= 0) return null
    return ((difference + 59_999L) / 60_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

@Composable
internal fun SnoozePickerDialog(
    title: String,
    description: String,
    onSnooze: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, color = MegadriveCyan) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description, color = MaterialTheme.colorScheme.onSurface)
                listOf(
                    listOf(15 to "15M", 30 to "30M", 60 to "1H"),
                    listOf(120 to "2H", 240 to "4H", 1440 to "24H")
                ).forEach { choices ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        choices.forEach { (minutes, label) ->
                            OutlinedButton(
                                onClick = { onSnooze(minutes) },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, MegadriveCyan),
                                contentPadding = PaddingValues(4.dp)
                            ) { Text(label, color = MegadriveCyan) }
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        val now = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        val target = Calendar.getInstance().apply {
                                            set(year, month, day, hour, minute, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                        val duration = minutesUntilSnooze(
                                            target.timeInMillis,
                                            System.currentTimeMillis()
                                        )
                                        if (duration == null) error = true else onSnooze(duration)
                                    },
                                    now.get(Calendar.HOUR_OF_DAY),
                                    now.get(Calendar.MINUTE),
                                    true
                                ).show()
                            },
                            now.get(Calendar.YEAR),
                            now.get(Calendar.MONTH),
                            now.get(Calendar.DAY_OF_MONTH)
                        ).apply {
                            val startOfToday = (now.clone() as Calendar).apply {
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            datePicker.minDate = startOfToday.timeInMillis
                            datePicker.maxDate = now.timeInMillis + 365L * 24 * 60 * 60 * 1000
                        }.show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MegadriveCyan)
                ) { Text("PICK DATE & TIME", color = MegadriveCyan) }
                if (error) Text("Choose a future time", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = MegadriveCyan) } }
    )
}
