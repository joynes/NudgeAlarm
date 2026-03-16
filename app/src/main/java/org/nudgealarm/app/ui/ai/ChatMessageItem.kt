package org.nudgealarm.app.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nudgealarm.app.ai.conversation.ChatMessage
import org.nudgealarm.app.ai.conversation.ConfirmationState

private val UserBubbleColor = Color(0xFF1565C0)
private val AssistantBubbleColor = Color(0xFF212121)
private val SystemColor = Color(0xFF37474F)
private val ToolCallColor = Color(0xFF1B5E20)
private val ToolResultColor = Color(0xFF263238)

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onConfirmToolCall: (String) -> Unit,
    onDenyToolCall: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (message) {
        is ChatMessage.User -> UserMessageBubble(message, modifier)
        is ChatMessage.Assistant -> AssistantMessageBubble(message, modifier)
        is ChatMessage.ToolCallMessage -> ToolConfirmationCard(
            message = message,
            onConfirm = { onConfirmToolCall(message.id) },
            onDeny = { onDenyToolCall(message.id) },
            modifier = modifier
        )
        is ChatMessage.ToolResultMessage -> ToolResultBubble(message, modifier)
        is ChatMessage.SystemMessage -> SystemMessageBubble(message, modifier)
    }
}

@Composable
private fun UserMessageBubble(message: ChatMessage.User, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(UserBubbleColor, RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.content,
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun AssistantMessageBubble(message: ChatMessage.Assistant, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(AssistantBubbleColor, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (message.content.isNotEmpty()) {
                Text(
                    text = message.content,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
            if (message.isStreaming) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = if (message.content.isNotEmpty()) 4.dp else 0.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = Color(0xFF90CAF9)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Thinking...",
                        color = Color(0xFF90CAF9),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolResultBubble(message: ChatMessage.ToolResultMessage, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(ToolResultColor, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = if (message.result.isError) Color(0xFFEF5350) else Color(0xFF66BB6A),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Tool: ${message.toolName}",
                    color = Color(0xFF90A4AE),
                    fontSize = 11.sp
                )
            }
            Text(
                text = message.result.text,
                color = if (message.result.isError) Color(0xFFEF9A9A) else Color(0xFFB0BEC5),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun SystemMessageBubble(message: ChatMessage.SystemMessage, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .background(SystemColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = Color(0xFFB0BEC5),
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = message.content,
                color = Color(0xFFB0BEC5),
                fontSize = 11.sp
            )
        }
    }
}
