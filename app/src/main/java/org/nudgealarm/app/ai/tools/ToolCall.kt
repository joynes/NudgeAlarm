package org.nudgealarm.app.ai.tools

data class ToolCall(
    val name: String,
    val arguments: Map<String, Any?>
)
