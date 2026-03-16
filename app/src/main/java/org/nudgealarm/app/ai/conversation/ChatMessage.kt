package org.nudgealarm.app.ai.conversation

import org.nudgealarm.app.ai.tools.ToolCall
import org.nudgealarm.app.ai.tools.ToolResult
import java.util.UUID

sealed class ChatMessage {
    abstract val id: String

    data class User(
        val content: String,
        override val id: String = UUID.randomUUID().toString()
    ) : ChatMessage()

    data class Assistant(
        val content: String,
        val isStreaming: Boolean = false,
        override val id: String = UUID.randomUUID().toString()
    ) : ChatMessage()

    data class ToolCallMessage(
        val toolCall: ToolCall,
        val confirmationState: ConfirmationState = ConfirmationState.PENDING,
        override val id: String = UUID.randomUUID().toString()
    ) : ChatMessage()

    data class ToolResultMessage(
        val toolName: String,
        val result: ToolResult,
        override val id: String = UUID.randomUUID().toString()
    ) : ChatMessage()

    data class SystemMessage(
        val content: String,
        override val id: String = UUID.randomUUID().toString()
    ) : ChatMessage()
}
