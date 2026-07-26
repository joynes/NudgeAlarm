package se.joynes.nudgealarm.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import se.joynes.nudgealarm.core.config.ReminderConfig
import se.joynes.nudgealarm.ui.theme.MegadriveCyan
import se.joynes.nudgealarm.ui.theme.MegadriveGold
import se.joynes.nudgealarm.ui.theme.MegadriveGreen
import se.joynes.nudgealarm.ui.theme.MegadrivePurple
import se.joynes.nudgealarm.ui.components.ButtonSound
import se.joynes.nudgealarm.ui.components.FeedbackButton

@Composable
fun RoutinesScreen(
    reminders: List<ReminderConfig>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // === HEADER - Quest List ===
        Column {
            Text(
                text = "QUEST LIST",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MegadriveGold
            )
            Text(
                text = "${reminders.size} quests loaded",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (reminders.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, MegadrivePurple, RoundedCornerShape(4.dp)),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "NO QUESTS LOADED",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MegadrivePurple
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Load a save file to see quests",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(reminders) { reminder ->
                    QuestDetailCard(reminder = reminder)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === BACK BUTTON ===
        FeedbackButton(
            text = "< BACK TO GAME",
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            color = MegadriveCyan,
            sound = ButtonSound.BACK
        )
    }
}

@Composable
private fun QuestDetailCard(reminder: ReminderConfig) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, MegadriveGreen, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "> ${reminder.title.uppercase()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MegadriveGreen
            )

            Spacer(modifier = Modifier.height(8.dp))

            StatRow(label = "ID", value = reminder.id)
            StatRow(label = "SCHEDULE", value = reminder.schedule)
            StatRow(label = "NAG INTERVAL", value = "${reminder.nagInterval.inWholeMinutes} min")
            StatRow(label = "MAX NAGS", value = "${reminder.maxNags}")
            StatRow(label = "SOUND", value = reminder.sound.uppercase())
            StatRow(label = "VIBRATION", value = reminder.vibration.uppercase())

            if (reminder.snoozeOptions.isNotEmpty()) {
                val snoozeText = reminder.snoozeOptions.joinToString(" | ") {
                    "${it.inWholeMinutes}m"
                }
                StatRow(label = "SNOOZE", value = snoozeText)
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MegadriveCyan
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
