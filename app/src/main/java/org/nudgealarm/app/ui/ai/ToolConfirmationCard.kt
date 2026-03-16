package org.nudgealarm.app.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nudgealarm.app.ai.conversation.ChatMessage
import org.nudgealarm.app.ai.conversation.ConfirmationState

private val PendingBorderColor = Color(0xFFF57F17)
private val ConfirmedBorderColor = Color(0xFF2E7D32)
private val DeniedBorderColor = Color(0xFFC62828)
private val CardBg = Color(0xFF1A1A2E)

@Composable
fun ToolConfirmationCard(
    message: ChatMessage.ToolCallMessage,
    onConfirm: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when (message.confirmationState) {
        ConfirmationState.PENDING -> PendingBorderColor
        ConfirmationState.CONFIRMED -> ConfirmedBorderColor
        ConfirmationState.DENIED -> DeniedBorderColor
    }

    val argsText = message.toolCall.arguments.entries.joinToString("\n") { (k, v) ->
        "  $k: $v"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Build,
                contentDescription = null,
                tint = PendingBorderColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Tool Call: ${message.toolCall.name}",
                color = PendingBorderColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (argsText.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = argsText,
                color = Color(0xFFCFD8DC),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        when (message.confirmationState) {
            ConfirmationState.PENDING -> {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ALLOW", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onDeny,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("DENY", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            ConfirmationState.CONFIRMED -> {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "✓ Allowed",
                    color = ConfirmedBorderColor,
                    fontSize = 11.sp
                )
            }
            ConfirmationState.DENIED -> {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "✗ Denied",
                    color = DeniedBorderColor,
                    fontSize = 11.sp
                )
            }
        }
    }
}
