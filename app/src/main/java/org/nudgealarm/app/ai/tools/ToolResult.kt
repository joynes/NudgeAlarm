package org.nudgealarm.app.ai.tools

sealed class ToolResult {
    data class Success(val data: String) : ToolResult()
    data class Error(val message: String) : ToolResult()

    val isError: Boolean get() = this is Error
    val text: String get() = when (this) {
        is Success -> data
        is Error -> "ERROR: $message"
    }
}
