package org.nudgealarm.app.ai.tools

import org.nudgealarm.app.database.NagRepository
import org.nudgealarm.app.database.ReminderRepository

class ListQuestsTool(private val reminderRepo: ReminderRepository) : AiTool {
    override val name = "listQuests"
    override val description = "List all quests (reminders). Returns id, title, schedule, nagIntervalMinutes, maxNags, enabled for each."
    override val parametersSchema = "{}"
    override val isWriteTool = false

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        return try {
            val reminders = reminderRepo.getAllReminders()
            val json = buildString {
                append("[")
                reminders.forEachIndexed { i, r ->
                    if (i > 0) append(",")
                    append("""{"id":"${r.id}","title":"${r.title}","schedule":"${r.schedule}","nagIntervalMinutes":${r.nagIntervalMinutes},"maxNags":${r.maxNags},"enabled":${r.enabled}}""")
                }
                append("]")
            }
            ToolResult.Success(json)
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Failed to list quests")
        }
    }
}

class GetQuestTool(private val reminderRepo: ReminderRepository) : AiTool {
    override val name = "getQuest"
    override val description = "Get details of a specific quest by its ID."
    override val parametersSchema = """{"id": "string (required) - the quest ID"}"""
    override val isWriteTool = false

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val id = args["id"] as? String ?: return ToolResult.Error("Missing required parameter: id")
        return try {
            val r = reminderRepo.getById(id) ?: return ToolResult.Error("Quest not found: $id")
            ToolResult.Success("""{"id":"${r.id}","title":"${r.title}","schedule":"${r.schedule}","nagIntervalMinutes":${r.nagIntervalMinutes},"maxNags":${r.maxNags},"enabled":${r.enabled},"createdAt":${r.createdAt},"updatedAt":${r.updatedAt}}""")
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Failed to get quest")
        }
    }
}

class SearchQuestsTool(private val reminderRepo: ReminderRepository) : AiTool {
    override val name = "searchQuests"
    override val description = "Search quests by title (case-insensitive substring match)."
    override val parametersSchema = """{"query": "string (required) - search term"}"""
    override val isWriteTool = false

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val query = args["query"] as? String ?: return ToolResult.Error("Missing required parameter: query")
        return try {
            val reminders = reminderRepo.getAllReminders()
                .filter { it.title.contains(query, ignoreCase = true) }
            val json = buildString {
                append("[")
                reminders.forEachIndexed { i, r ->
                    if (i > 0) append(",")
                    append("""{"id":"${r.id}","title":"${r.title}","schedule":"${r.schedule}","enabled":${r.enabled}}""")
                }
                append("]")
            }
            ToolResult.Success(json)
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Failed to search quests")
        }
    }
}

class ListActiveQuestsTool(private val nagRepo: NagRepository) : AiTool {
    override val name = "listActiveQuests"
    override val description = "List quests that are currently active (nagging / awaiting completion)."
    override val parametersSchema = "{}"
    override val isWriteTool = false

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        return try {
            val active = nagRepo.getActiveNags()
            val json = buildString {
                append("[")
                active.forEachIndexed { i, n ->
                    if (i > 0) append(",")
                    append("""{"ruleId":"${n.ruleId}","title":"${n.title}","status":"${n.status}","nagCount":${n.nagCount},"scheduledTime":${n.scheduledTime}}""")
                }
                append("]")
            }
            ToolResult.Success(json)
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Failed to list active quests")
        }
    }
}

class GetQuestsByIdsTool(private val reminderRepo: ReminderRepository) : AiTool {
    override val name = "getQuestsByIds"
    override val description = "Get multiple quests by their IDs."
    override val parametersSchema = """{"ids": "array of strings (required) - quest IDs to retrieve"}"""
    override val isWriteTool = false

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        @Suppress("UNCHECKED_CAST")
        val ids = (args["ids"] as? List<*>)?.filterIsInstance<String>()
            ?: return ToolResult.Error("Missing required parameter: ids (must be an array of strings)")
        return try {
            val reminders = ids.mapNotNull { reminderRepo.getById(it) }
            val json = buildString {
                append("[")
                reminders.forEachIndexed { i, r ->
                    if (i > 0) append(",")
                    append("""{"id":"${r.id}","title":"${r.title}","schedule":"${r.schedule}","nagIntervalMinutes":${r.nagIntervalMinutes},"maxNags":${r.maxNags},"enabled":${r.enabled}}""")
                }
                append("]")
            }
            ToolResult.Success(json)
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Failed to get quests by IDs")
        }
    }
}
