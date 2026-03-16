package org.nudgealarm.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.nudgealarm.app.ui.theme.MegadriveCyan
import org.nudgealarm.app.ui.theme.MegadriveGold
import org.nudgealarm.app.ui.theme.MegadriveGreen
import org.nudgealarm.app.ui.theme.MegadriveOrange
import org.nudgealarm.app.ui.theme.MegadrivePurple
import org.nudgealarm.app.ui.theme.MegadriveRed

data class PermissionState(
    val name: String,
    val description: String,
    val isGranted: Boolean,
    val onRequest: () -> Unit
)

@Composable
fun PermissionScreen(
    onRequestNotificationPermission: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionStates by remember { mutableStateOf(getPermissionStates(context, onRequestNotificationPermission)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionStates = getPermissionStates(context, onRequestNotificationPermission)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // === HEADER - System Check ===
        Column {
            Text(
                text = "SYSTEM CHECK",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MegadriveGold
            )
            Text(
                text = "Required power-ups for the game",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            permissionStates.forEach { permission ->
                PermissionCard(permission = permission)
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
private fun PermissionCard(permission: PermissionState) {
    val borderColor = if (permission.isGranted) MegadriveGreen else MegadriveRed
    val statusColor = if (permission.isGranted) MegadriveGreen else MegadriveRed
    val statusText = if (permission.isGranted) "UNLOCKED" else "LOCKED"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (permission.isGranted) "* " else "! ",
                            color = statusColor,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = permission.name.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                    Text(
                        text = permission.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!permission.isGranted) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = permission.onRequest,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MegadrivePurple)
                ) {
                    Text(">> UNLOCK", color = Color.White)
                }
            }
        }
    }
}

private fun getPermissionStates(
    context: Context,
    onRequestNotificationPermission: () -> Unit
): List<PermissionState> {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    return listOf(
        PermissionState(
            name = "Notifications",
            description = "Required to show quest alerts",
            isGranted = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            onRequest = onRequestNotificationPermission
        ),
        PermissionState(
            name = "Battery Saver",
            description = "Disable to prevent game from sleeping",
            isGranted = powerManager.isIgnoringBatteryOptimizations(context.packageName),
            onRequest = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        ),
        PermissionState(
            name = "App Settings",
            description = "Access additional configuration",
            isGranted = true,
            onRequest = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        )
    )
}
