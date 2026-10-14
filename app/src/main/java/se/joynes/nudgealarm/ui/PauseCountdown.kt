package se.joynes.nudgealarm.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import se.joynes.nudgealarm.ui.theme.MegadriveGreen
import se.joynes.nudgealarm.ui.theme.MegadrivePurple
import se.joynes.nudgealarm.ui.theme.MegadriveRed
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date

internal fun pauseRemainingLabel(until: Long, now: Long): String {
    val remaining = until - now
    if (remaining <= 0) return "Resuming…"
    if (remaining < 60_000L) return "${(remaining + 999L) / 1000L}s left"
    val minutes = (remaining + 59_999L) / 60_000L
    val days = minutes / 1440L
    val hours = minutes % 1440L / 60L
    val rest = minutes % 60L
    val parts = listOfNotNull(if (days > 0) "${days}d" else null,
        if (hours > 0) "${hours}h" else null, if (rest > 0) "${rest}m" else null)
    return "${parts.joinToString(" ")} left"
}

internal fun pauseEndLabel(until: Long, now: Long): String {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val endDay = Instant.ofEpochMilli(until).atZone(zone).toLocalDate()
    val format = if (today == endDay) DateFormat.getTimeInstance(DateFormat.SHORT)
        else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    return "until ${format.format(Date(until))}"
}

internal fun countdownRefreshDelay(deadlines: List<Long>, now: Long): Long? = deadlines.filter { it > now }.minOfOrNull {
    val remaining = it - now
    if (remaining <= 60_000L) remaining.coerceAtMost(1000L)
    else (remaining % 60_000L).takeIf { rest -> rest > 0L } ?: 60_000L
}

/** One foreground-only clock for all visible pauses; minute updates, seconds in the final minute. */
@Composable
internal fun rememberCountdownNow(deadlines: List<Long>): Long {
    val lifecycleOwner = LocalLifecycleOwner.current
    val now by produceState(System.currentTimeMillis(), lifecycleOwner, deadlines) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = System.currentTimeMillis()
                val refresh = countdownRefreshDelay(deadlines, value) ?: break
                delay(refresh)
            }
        }
    }
    return now
}

@Composable
internal fun PauseAlertIndicator(quietMode: Boolean, onToggle: () -> Unit) {
        IconButton(onClick = onToggle, modifier = Modifier.size(40.dp).testTag("pause-alerts-icon")) {
            Icon(imageVector = if (quietMode) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                contentDescription = if (quietMode) "Unmute" else "Mute",
                tint = if (quietMode) MegadriveRed else MegadriveGreen, modifier = Modifier.size(22.dp))
        }
}

@Composable
internal fun PauseStatusBanner(until: Long, now: Long, onResume: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().testTag("pause-status-banner"),
        shape = RoundedCornerShape(10.dp), color = MegadrivePurple.copy(alpha = 0.12f)) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsOff, contentDescription = null, tint = MegadrivePurple, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (until > 0L) pauseRemainingLabel(until, now) else "Alerts paused",
                    fontFamily = FontFamily.SansSerif, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("pause-alerts-remaining"))
                if (until > 0L) Text("Alerts paused · ${pauseEndLabel(until, now)}", fontFamily = FontFamily.SansSerif, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("pause-alerts-until"))
                else Text("Until you resume", fontFamily = FontFamily.SansSerif, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onResume, modifier = Modifier.testTag("resume-alerts")) {
                Text("RESUME", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
internal fun SnoozedQuestCountdown(until: Long, now: Long, modifier: Modifier = Modifier) {
    val remaining = pauseRemainingLabel(until, now)
    Row(modifier.background(MegadrivePurple.copy(alpha = 0.14f), RoundedCornerShape(5.dp))
        .semantics(mergeDescendants = true) { contentDescription = "Snoozed, $remaining, ${pauseEndLabel(until, now)}" }
        .padding(horizontal = 6.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Snooze, contentDescription = null, tint = MegadrivePurple, modifier = Modifier.size(12.dp))
        Text(remaining.removeSuffix(" left"), color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp)
    }
}
