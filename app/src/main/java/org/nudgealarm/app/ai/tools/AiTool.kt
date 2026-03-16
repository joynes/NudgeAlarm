package org.nudgealarm.app.ai.tools

interface AiTool {
    val name: String
    val description: String
    val parametersSchema: String  // JSON schema description for system prompt
    val isWriteTool: Boolean

    suspend fun execute(args: Map<String, Any?>): ToolResult
}
