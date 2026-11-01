package se.joynes.nudgealarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.joynes.nudgealarm.ui.theme.MegadriveCyan
import se.joynes.nudgealarm.ui.theme.MegadriveGold
import se.joynes.nudgealarm.ui.theme.MegadriveGreen
import se.joynes.nudgealarm.ui.theme.MegadriveOrange
import se.joynes.nudgealarm.ui.theme.MegadrivePurple
import se.joynes.nudgealarm.ui.theme.MegadriveRed

@Composable
fun AnalyticsScreen(
    uiState: AnalyticsUiState,
    onSelectTimeRange: (TimeRange) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // === HEADER ===
        Text(
            text = "HIGH SCORES",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MegadriveGold
        )
        Text(
            text = "Quest completion stats",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // === TIME RANGE SELECTOR ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimeRange.entries.forEach { range ->
                FilterChip(
                    selected = uiState.timeRange == range,
                    onClick = { onSelectTimeRange(range) },
                    label = {
                        Text(
                            text = when (range) {
                                TimeRange.WEEK -> "1W"
                                TimeRange.MONTH -> "1M"
                                TimeRange.YEAR -> "1Y"
                                TimeRange.ALL -> "ALL"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MegadrivePurple,
                        selectedLabelColor = Color.White
                    ),
                    border = if (uiState.timeRange == range) null else BorderStroke(1.dp, MegadriveCyan)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MegadriveCyan)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("LOADING DATA...", color = MegadriveCyan)
                }
            }
        } else if (!uiState.hasData) {
            NoDataPlaceholder(modifier = Modifier.weight(1f))
        } else {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // === SCORE BOARD ===
                SummaryCard(
                    completed = uiState.completedCount,
                    expired = uiState.expiredCount,
                    cancelled = uiState.cancelledCount,
                    completionRate = uiState.completionRate
                )

                // === COMPLETION % LINE CHART ===
                if (uiState.chartData.isNotEmpty()) {
                    OutcomeChart(
                        chartData = uiState.chartData,
                        modifier = Modifier.weight(1f)
                    )
                }

                // === COOL STATS ROW ===
                StatsRow(
                    currentStreak = uiState.currentStreak,
                    bestStreak = uiState.bestStreak,
                    totalBattles = uiState.totalBattles,
                    avgResponseTimeMs = uiState.avgResponseTimeMs
                )
            }
        }
    }
}

@Composable
private fun NoDataPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
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
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "NO DATA YET",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MegadrivePurple
                )
                Text(
                    text = "Complete some quests to unlock",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "your achievement stats!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    completed: Int,
    expired: Int,
    cancelled: Int,
    completionRate: Float
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, MegadriveGold, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(completionRate * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MegadriveGreen
                    )
                    Text(
                        text = "Completed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MegadriveGold
                    )
                }
                Column {
                    Text("Completed: $completed", color = MegadriveGreen, style = MaterialTheme.typography.bodySmall)
                    Text("Expired: $expired", color = MegadriveRed, style = MaterialTheme.typography.bodySmall)
                    Text("Abandoned: $cancelled", color = MegadriveOrange, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Expired = automatic timeout • Abandoned = you chose Abandon",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@Composable
private fun StatsRow(
    currentStreak: Int,
    bestStreak: Int,
    totalBattles: Int,
    avgResponseTimeMs: Long
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, MegadrivePurple, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MiniStat(label = "STREAK", value = "${currentStreak}d", color = MegadriveGreen)
            MiniStat(label = "BEST", value = "${bestStreak}d", color = MegadriveGold)
            MiniStat(label = "BATTLES", value = totalBattles.toString(), color = MegadriveCyan)
            MiniStat(
                label = "SPEED",
                value = formatDuration(avgResponseTimeMs),
                color = MegadrivePurple
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "-"
    val seconds = ms / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> "${hours}h${minutes % 60}m"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}
