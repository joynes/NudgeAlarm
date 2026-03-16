package org.nudgealarm.app.ai.tools

class ToolRegistry {
    private val tools = mutableMapOf<String, AiTool>()

    fun register(tool: AiTool) {
        tools[tool.name] = tool
    }

    fun find(name: String): AiTool? = tools[name]

    fun listAll(): List<AiTool> = tools.values.toList()

    fun listReadTools(): List<AiTool> = tools.values.filter { !it.isWriteTool }

    fun listWriteTools(): List<AiTool> = tools.values.filter { it.isWriteTool }
}
