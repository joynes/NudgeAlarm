package se.joynes.nudgealarm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.joynes.nudgealarm.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Keep every outcome while limiting long ranges to readable, consecutive date buckets. */
internal fun outcomeChartBuckets(days: List<ChartDataPoint>): List<ChartDataPoint> {
    val chunkSize = ((days.size + 29) / 30).coerceAtLeast(1)
    val format = SimpleDateFormat("d MMM yy", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }
    return days.chunked(chunkSize).map { chunk ->
        val start = format.format(Date(chunk.first().date))
        val end = format.format(Date(chunk.last().date))
        ChartDataPoint(chunk.first().date, if (chunk.size == 1) start else "$start – $end",
            chunk.sumOf { it.completed }, chunk.sumOf { it.expired }, chunk.sumOf { it.cancelled })
    }
}

@Composable
internal fun OutcomeChart(chartData: List<ChartDataPoint>, modifier: Modifier = Modifier) {
    val buckets = remember(chartData) { outcomeChartBuckets(chartData) }
    var selection by remember(buckets) { mutableIntStateOf(buckets.indexOfLast { it.total > 0 }.coerceAtLeast(0)) }
    val selected = buckets.getOrNull(selection)
    val maxCount = (buckets.maxOfOrNull { it.total } ?: 0).coerceAtLeast(1)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = .25f)
    Card(modifier = modifier.fillMaxWidth().testTag("outcome-chart"), shape = RoundedCornerShape(4.dp),
        border = BorderStroke(2.dp, MegadriveCyan),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("QUEST OUTCOMES", style = MaterialTheme.typography.titleMedium, color = MegadriveCyan)
            Text("Number of quests • Tap a bar for details", style = MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("✓ Done", color = MegadriveGreen, style = MaterialTheme.typography.labelSmall)
                Text("! Expired", color = MegadriveRed, style = MaterialTheme.typography.labelSmall)
                Text("× Abandoned", color = MegadriveOrange, style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Canvas(Modifier.width(28.dp).fillMaxHeight()) {
                    val paint = android.graphics.Paint().apply { color = textColor.toArgb(); textSize = 10.sp.toPx() }
                    drawContext.canvas.nativeCanvas.drawText(maxCount.toString(), 0f, 12.sp.toPx(), paint)
                    drawContext.canvas.nativeCanvas.drawText("0", 0f, size.height - 2.dp.toPx(), paint)
                }
                Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    buckets.forEachIndexed { index, point ->
                        Canvas(Modifier.weight(1f).fillMaxHeight().testTag("outcome-bar-$index")
                            .semantics { contentDescription = "${point.label}: ${point.completed} completed, ${point.expired} expired, ${point.cancelled} abandoned" }
                            .clickable { selection = index }) {
                            drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                            val scale = (size.height - 12.dp.toPx()).coerceAtLeast(0f) / maxCount
                            var y = size.height
                            listOf(point.completed to MegadriveGreen, point.expired to MegadriveRed,
                                point.cancelled to MegadriveOrange).forEach { (count, color) ->
                                val height = count * scale
                                y -= height
                                if (height > 0) drawRect(color, Offset(0f, y), Size(size.width, height))
                            }
                            if (selection == index) drawLine(MegadriveCyan, Offset(size.width / 2, 0f),
                                Offset(size.width / 2, size.height), 1.dp.toPx(), alpha = .55f)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(buckets.firstOrNull()?.label?.substringBefore(" – ") ?: "", style = MaterialTheme.typography.labelSmall)
                Text(buckets.lastOrNull()?.label?.substringAfter(" – ") ?: "", style = MaterialTheme.typography.labelSmall)
            }
            if (selected != null) {
                Text(selected.label, style = MaterialTheme.typography.labelSmall, color = MegadriveCyan)
                Text("${selected.completed} done • ${selected.expired} expired • ${selected.cancelled} abandoned",
                    modifier = Modifier.testTag("outcome-details"), style = MaterialTheme.typography.labelSmall)
                if (selected.total == 0) Text("No finished quests in this period", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
