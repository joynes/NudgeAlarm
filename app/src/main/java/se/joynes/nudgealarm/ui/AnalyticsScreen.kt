package se.joynes.nudgealarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
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
                    CompletionLineChart(
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatItem(label = "WIN", value = completed.toString(), color = MegadriveGreen)
                    StatItem(label = "LOSE", value = expired.toString(), color = MegadriveRed)
                    StatItem(label = "FLEE", value = cancelled.toString(), color = MegadriveOrange)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(completionRate * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            completionRate >= 0.8f -> MegadriveGreen
                            completionRate >= 0.5f -> MegadriveOrange
                            else -> MegadriveRed
                        }
                    )
                    Text(
                        text = when {
                            completionRate >= 0.9f -> "LEGENDARY"
                            completionRate >= 0.8f -> "CHAMPION"
                            completionRate >= 0.6f -> "WARRIOR"
                            completionRate >= 0.4f -> "APPRENTICE"
                            else -> "ROOKIE"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MegadriveGold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompletionLineChart(
    chartData: List<ChartDataPoint>,
    modifier: Modifier = Modifier
) {
    val percentages = chartData.map { point ->
        if (point.total > 0) point.completed.toFloat() / point.total * 100f else 0f
    }
    val lineColor = MegadriveGreen
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, MegadriveCyan, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Text(
                text = ">> BATTLE HISTORY — WIN %",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MegadriveCyan
            )
            Spacer(modifier = Modifier.height(8.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                if (percentages.isEmpty()) return@Canvas

                val paddingLeft = 36.dp.toPx()
                val paddingBottom = 20.dp.toPx()
                val paddingTop = 8.dp.toPx()
                val paddingRight = 8.dp.toPx()
                val chartWidth = size.width - paddingLeft - paddingRight
                val chartHeight = size.height - paddingTop - paddingBottom

                // Draw horizontal grid lines at 0%, 25%, 50%, 75%, 100%
                for (i in 0..4) {
                    val y = paddingTop + chartHeight * (1f - i / 4f)
                    drawLine(
                        color = gridColor,
                        start = Offset(paddingLeft, y),
                        end = Offset(size.width - paddingRight, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Draw Y-axis labels
                val labelPositions = listOf(0, 50, 100)
                for (pct in labelPositions) {
                    val y = paddingTop + chartHeight * (1f - pct / 100f)
                    drawContext.canvas.nativeCanvas.drawText(
                        "${pct}%",
                        4.dp.toPx(),
                        y + 4.dp.toPx(),
                        android.graphics.Paint().apply {
                            color = textColor.hashCode()
                            textSize = 10.sp.toPx()
                        }
                    )
                }

                if (percentages.size == 1) {
                    // Single point: draw a dot
                    val x = paddingLeft + chartWidth / 2f
                    val y = paddingTop + chartHeight * (1f - percentages[0] / 100f)
                    drawCircle(
                        color = lineColor,
                        radius = 4.dp.toPx(),
                        center = Offset(x, y)
                    )
                    return@Canvas
                }

                // Build line path
                val path = Path()
                val stepX = chartWidth / (percentages.size - 1).coerceAtLeast(1)

                percentages.forEachIndexed { index, pct ->
                    val x = paddingLeft + stepX * index
                    val y = paddingTop + chartHeight * (1f - pct / 100f)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                // Draw the line
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Draw dots on each data point
                percentages.forEachIndexed { index, pct ->
                    val x = paddingLeft + stepX * index
                    val y = paddingTop + chartHeight * (1f - pct / 100f)
                    drawCircle(
                        color = lineColor,
                        radius = 3.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
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
