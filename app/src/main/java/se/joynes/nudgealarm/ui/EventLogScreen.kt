package se.joynes.nudgealarm.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import se.joynes.nudgealarm.core.event.Event
import se.joynes.nudgealarm.ui.theme.MegadriveCyan
import se.joynes.nudgealarm.ui.theme.MegadriveGold
import se.joynes.nudgealarm.ui.theme.MegadriveGreen
import se.joynes.nudgealarm.ui.theme.MegadriveOrange
import se.joynes.nudgealarm.ui.theme.MegadrivePurple
import se.joynes.nudgealarm.ui.theme.MegadriveRed

@Composable
fun EventLogScreen(
    events: List<Event>,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // === HEADER - Debug Console ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DEBUG LOG",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MegadriveGold
                )
                Text(
                    text = "${events.size} events recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onClear,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, MegadriveOrange)
            ) {
                Text("CLEAR", color = MegadriveOrange)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (events.isEmpty()) {
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
                        text = "LOG EMPTY",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MegadrivePurple
                    )
                    Text(
                        text = "No events recorded yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(events) { event ->
                    EventCard(event = event)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === BACK BUTTON ===
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MegadriveCyan)
        ) {
            Text("< BACK TO GAME", color = Color.White)
        }
    }
}

@Composable
private fun EventCard(event: Event) {
    val isCritical = event is Event.ServiceKilled || event is Event.ConfigError || event is Event.Crash
    val isWarning = event is Event.ServiceStopped
    val isSuccess = event is Event.ServiceStarted || event is Event.UserTappedDone

    val borderColor = when {
        isCritical -> MegadriveRed
        isWarning -> MegadriveOrange
        isSuccess -> MegadriveGreen
        else -> MaterialTheme.colorScheme.outline
    }

    val textColor = when {
        isCritical -> MegadriveRed
        isWarning -> MegadriveOrange
        isSuccess -> MegadriveGreen
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = event.formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MegadriveCyan
            )
            Text(
                text = event.message,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
