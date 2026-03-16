package org.nudgealarm.app.ai.conversation

import org.nudgealarm.app.ai.model.ModelInfo
import org.nudgealarm.app.ai.tools.AiTool
import org.nudgealarm.app.ai.tools.ToolCall

private const val MAX_HISTORY_TURNS = 4
private const val MAX_TOOL_RESULT_CHARS = 800

class ConversationManager {

    /**
     * questContext: compact pre-fetched quest list to embed in system prompt.
     * By injecting data directly, the 1B model never needs to call read tools.
     */
    fun buildSystemPrompt(writeTools: List<AiTool>, questContext: String): String {
        val writeDefs = writeTools.joinToString("\n") { "- ${it.name}: ${it.description}" }
        val questSection = if (questContext.isNotBlank()) "\nUSER'S QUESTS:\n$questContext\n" else ""
        val writeSection = if (writeTools.isNotEmpty()) """

WRITE TOOLS (user confirms before execution):
$writeDefs

To call a write tool, output on its own line:
TOOL:toolName {"arg":"val"}
Example: TOOL:createQuest {"title":"Morning Run","schedule":"0 7 * * 1-5"}
Example: TOOL:deleteQuest {"id":"some_id"}""" else ""
        return """You are a helpful assistant in NudgeAlarm (habit reminder app). Quests = reminders.
Today: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date())}.
Cron format: "minute hour dayOfMonth month dayOfWeek" e.g. "0 9 * * 1-5" = Mon-Fri 9am.$questSection$writeSection

Answer concisely. Use the quest data above to answer any quest questions."""
    }

    fun buildPrompt(messages: List<ChatMessage>, model: ModelInfo, writeTools: List<AiTool>, questContext: String): String {
        val systemPrompt = buildSystemPrompt(writeTools, questContext)

        // Limit to last MAX_HISTORY_TURNS turns
        val relevantMessages = if (messages.size > MAX_HISTORY_TURNS * 2) {
            messages.takeLast(MAX_HISTORY_TURNS * 2)
        } else {
            messages
        }

        return when (model.id) {
            "phi2" -> buildPhi2Prompt(systemPrompt, relevantMessages)
            "gemma3_1b" -> buildGemma3Prompt(systemPrompt, relevantMessages)
            else -> buildGemmaPrompt(systemPrompt, relevantMessages)
        }
    }

    /**
     * Gemma 3 uses a dedicated <start_of_turn>system role instead of stuffing
     * the system prompt into a user turn. The <bos> token is added automatically
     * by the tokenizer, so we don't include it in the text.
     */
    private fun buildGemma3Prompt(systemPrompt: String, messages: List<ChatMessage>): String {
        return buildString {
            append("<start_of_turn>system\n")
            append(systemPrompt)
            append("<end_of_turn>\n")

            messages.forEach { msg ->
                when (msg) {
                    is ChatMessage.User -> {
                        append("<start_of_turn>user\n${msg.content}<end_of_turn>\n")
                    }
                    is ChatMessage.Assistant -> {
                        append("<start_of_turn>model\n${msg.content}<end_of_turn>\n")
                    }
                    is ChatMessage.ToolCallMessage -> {
                        val tc = msg.toolCall
                        val argsJson = tc.arguments.entries.joinToString(",") { (k, v) ->
                            """"$k":${valueToJson(v)}"""
                        }
                        append("<start_of_turn>model\nTOOL:${tc.name} {$argsJson}<end_of_turn>\n")
                    }
                    is ChatMessage.ToolResultMessage -> {
                        val truncated = msg.result.text.take(MAX_TOOL_RESULT_CHARS)
                        append("<start_of_turn>user\nTool result for ${msg.toolName}: $truncated<end_of_turn>\n")
                    }
                    is ChatMessage.SystemMessage -> {
                        append("<start_of_turn>user\n[System: ${msg.content}]<end_of_turn>\n")
                    }
                }
            }

            append("<start_of_turn>model\n")
        }
    }

    private fun buildGemmaPrompt(systemPrompt: String, messages: List<ChatMessage>): String {
        return buildString {
            append("<start_of_turn>user\n")
            append(systemPrompt)
            append("<end_of_turn>\n")
            append("<start_of_turn>model\nUnderstood. I am ready to help manage your quests.<end_of_turn>\n")

            messages.forEach { msg ->
                when (msg) {
                    is ChatMessage.User -> {
                        append("<start_of_turn>user\n${msg.content}<end_of_turn>\n")
                    }
                    is ChatMessage.Assistant -> {
                        append("<start_of_turn>model\n${msg.content}<end_of_turn>\n")
                    }
                    is ChatMessage.ToolCallMessage -> {
                        val tc = msg.toolCall
                        val argsJson = tc.arguments.entries.joinToString(",") { (k, v) ->
                            """"$k":${valueToJson(v)}"""
                        }
                        append("<start_of_turn>model\nTOOL:${tc.name} {$argsJson}<end_of_turn>\n")
                    }
                    is ChatMessage.ToolResultMessage -> {
                        val truncated = msg.result.text.take(MAX_TOOL_RESULT_CHARS)
                        append("<start_of_turn>user\nTool result for ${msg.toolName}: $truncated<end_of_turn>\n")
                    }
                    is ChatMessage.SystemMessage -> {
                        append("<start_of_turn>user\n[System: ${msg.content}]<end_of_turn>\n")
                    }
                }
            }

            append("<start_of_turn>model\n")
        }
    }

    private fun buildPhi2Prompt(systemPrompt: String, messages: List<ChatMessage>): String {
        return buildString {
            append("Instruct: $systemPrompt\n\n")

            messages.forEach { msg ->
                when (msg) {
                    is ChatMessage.User -> append("Instruct: ${msg.content}\n")
                    is ChatMessage.Assistant -> append("Output: ${msg.content}\n")
                    is ChatMessage.ToolCallMessage -> {
                        val tc = msg.toolCall
                        val argsJson = tc.arguments.entries.joinToString(",") { (k, v) ->
                            """"$k":${valueToJson(v)}"""
                        }
                        append("Output: TOOL:${tc.name} {$argsJson}\n")
                    }
                    is ChatMessage.ToolResultMessage -> {
                        val truncated = msg.result.text.take(MAX_TOOL_RESULT_CHARS)
                        append("Instruct: Tool result for ${msg.toolName}: $truncated\n")
                    }
                    is ChatMessage.SystemMessage -> {
                        append("Instruct: [System: ${msg.content}]\n")
                    }
                }
            }

            append("Output:")
        }
    }

    private fun valueToJson(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"${value.replace("\"", "\\\"")}\""
        is Number -> value.toString()
        is Boolean -> value.toString()
        is List<*> -> "[${value.joinToString(",") { valueToJson(it) }}]"
        is Map<*, *> -> "{${value.entries.joinToString(",") { (k, v) -> "\"$k\":${valueToJson(v)}" }}}"
        else -> "\"$value\""
    }

    /**
     * Parse a tool call from model output.
     * Format: TOOL:toolName {"arg":"val"}
     * Returns ToolCall if found, null otherwise.
     */
    fun parseToolCall(text: String): ToolCall? {
        // Match "TOOL:name {json}" — name is word chars, then optional whitespace, then JSON object
        val pattern = Regex("""TOOL:(\w+)\s+(\{.*\})""", setOf(RegexOption.DOT_MATCHES_ALL))
        val match = pattern.find(text) ?: return null

        val name = match.groupValues[1].trim()
        val argsJson = match.groupValues[2].trim()

        return try {
            val args = parseJsonObject(argsJson)
            ToolCall(name = name, arguments = args)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns the text before any tool call, trimmed.
     */
    fun textBeforeToolCall(text: String): String {
        val idx = text.indexOf("TOOL:")
        return if (idx >= 0) text.substring(0, idx).trim() else text.trim()
    }

    /**
     * Strips model-emitted special tokens that should not appear in the displayed text.
     * Gemma models often output <end_of_turn> at the end of their response.
     */
    fun postProcessResponse(text: String): String {
        return text
            .replace("<end_of_turn>", "")
            .replace("<eos>", "")
            .replace("<start_of_turn>model", "")
            .replace("<start_of_turn>user", "")
            .replace("<start_of_turn>system", "")
            .trim()
    }

    /**
     * Very simple JSON object parser for tool arguments.
     * Supports string, number, boolean, null, nested objects, and arrays.
     */
    private fun parseJsonObject(json: String): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        val trimmed = json.trim().removePrefix("{").removeSuffix("}")
        if (trimmed.isBlank()) return result

        var pos = 0
        while (pos < trimmed.length) {
            // Skip whitespace and commas
            while (pos < trimmed.length && (trimmed[pos] == ',' || trimmed[pos].isWhitespace())) pos++
            if (pos >= trimmed.length) break

            // Parse key
            if (trimmed[pos] != '"') break
            val keyEnd = trimmed.indexOf('"', pos + 1)
            if (keyEnd < 0) break
            val key = trimmed.substring(pos + 1, keyEnd)
            pos = keyEnd + 1

            // Skip colon
            while (pos < trimmed.length && (trimmed[pos] == ':' || trimmed[pos].isWhitespace())) pos++

            // Parse value
            val (value, newPos) = parseJsonValue(trimmed, pos)
            result[key] = value
            pos = newPos
        }
        return result
    }

    private fun parseJsonValue(json: String, startPos: Int): Pair<Any?, Int> {
        var pos = startPos
        while (pos < json.length && json[pos].isWhitespace()) pos++
        if (pos >= json.length) return Pair(null, pos)

        return when {
            json[pos] == '"' -> {
                // String
                val sb = StringBuilder()
                pos++
                while (pos < json.length && json[pos] != '"') {
                    if (json[pos] == '\\' && pos + 1 < json.length) {
                        pos++
                        sb.append(when (json[pos]) {
                            'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'
                            '"' -> '"'; '\\' -> '\\'
                            else -> json[pos]
                        })
                    } else {
                        sb.append(json[pos])
                    }
                    pos++
                }
                Pair(sb.toString(), pos + 1)
            }
            json[pos] == '{' -> {
                // Nested object - find matching brace
                var depth = 0
                val start = pos
                while (pos < json.length) {
                    when (json[pos]) {
                        '{' -> depth++
                        '}' -> { depth--; if (depth == 0) { pos++; break } }
                    }
                    pos++
                }
                Pair(parseJsonObject(json.substring(start, pos)), pos)
            }
            json[pos] == '[' -> {
                // Array
                val list = mutableListOf<Any?>()
                pos++ // skip [
                while (pos < json.length && json[pos] != ']') {
                    while (pos < json.length && (json[pos] == ',' || json[pos].isWhitespace())) pos++
                    if (pos < json.length && json[pos] != ']') {
                        val (v, newPos) = parseJsonValue(json, pos)
                        list.add(v)
                        pos = newPos
                    }
                }
                Pair(list, pos + 1)
            }
            json.startsWith("true", pos) -> Pair(true, pos + 4)
            json.startsWith("false", pos) -> Pair(false, pos + 5)
            json.startsWith("null", pos) -> Pair(null, pos + 4)
            else -> {
                // Number
                val remaining = json.substring(pos)
                val relEnd = remaining.indexOfFirst { !it.isDigit() && it != '.' && it != '-' && it != 'e' && it != 'E' && it != '+' }
                val end = if (relEnd < 0) json.length else pos + relEnd
                val numStr = json.substring(pos, end)
                val num = numStr.toDoubleOrNull() ?: 0.0
                Pair(if (numStr.contains('.')) num else num.toLong(), end)
            }
        }
    }
}
