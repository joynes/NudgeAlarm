package org.nudgealarm.app.ai.tools

import org.nudgealarm.app.database.ReminderEntity
import org.nudgealarm.app.database.ReminderRepository

class CreateQuestTool(private val reminderRepo: ReminderRepository) : AiTool {
    override val name = "createQuest"
    override val description = "Create a new quest (reminder). Schedule is a cron expression (e.g. '0 9 * * 1-5' for Mon-Fri 9am)."
    override val parametersSchema = """{"title":"string (required)","schedule":"string (required, cron)","nagIntervalMinutes":"int (optional, default 5)","maxNags":"int (optional, default 100)"}"""
    override val isWriteTool = true

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val title = args["title"] as? String ?: return ToolResult.Error("Missing required parameter: title")
        val schedule = args["schedule"] as? String ?: return ToolResult.Error("Missing required parameter: schedule")
        val nagInterval = (args["nagIntervalMinutes"] as? Number)?.toInt() ?: 5
        val maxNags = (args["maxNags"] as? Number)?.toInt() ?: 100
        return try {
            val created = reminderRepo.create(title, schedule, nagInterval, maxNags)
            ToolResult.Success("""{"id":"${created.id}","title":"${created.title}","schedule":"${created.schedule}"}""")
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Failed to create quest")
        }
    }
}

class UpdateQuestTool(private val reminderRepo: ReminderRepository) : AiTool {
    override val name = "updateQuest"
    override val description = "Update an existing quest. Only provided fields are changed."
    override val parametersSchema = """{"id":"string (required)","title":"string (optional)","schedule":"string (optional, cron)","nagIntervalMinutes":"int (optional)","maxNags":"int (optional)","enabled":"boolean (optional)"}"""
    override val isWriteTool = true

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val id = args["id"] as? String ?: return ToolResult.Error("Missing required parameter: id")
        return try {
            val existing = reminderRepo.getById(id) ?: return ToolResult.Error("Quest not found: $id")
            val updated = existing.copy(
                title = args["title"] as? String ?: existing.title,
                schedule = args["schedule"] as? String ?: existing.schedule,
                nagIntervalMinutes = (args["nagIntervalMinutes"] as? Number)?.toInt() ?: existing.nagIntervalMinutes,
                maxNags = (args["maxNags"] as? Number)?.toInt() ?: existing.maxNags,
                enabled = args["enabled"] as? Boolean ?: existing.enabled
            )
            reminderRepo.update(updated)
            ToolResult.Success("""{"id":"${updated.id}","title":"${updated.title}","schedule":"${updated.schedule}","enabled":${updated.enabled}}""")
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Failed to update quest")
        }
    }
}

class DeleteQuestTool(private val reminderRepo: ReminderRepository) : AiTool {
    override val name = "deleteQuest"
    override val description = "Delete a quest by ID. This is irreversible."
    override val parametersSchema = """{"id":"string (required)"}"""
    override val isWriteTool = true

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val id = args["id"] as? String ?: return ToolResult.Error("Missing required parameter: id")
        return try {
            reminderRepo.delete(id)
            ToolResult.Success("""{"deleted":"$id"}""")
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Failed to delete quest")
        }
    }
}

class CreateQuestsBatchTool(private val reminderRepo: ReminderRepository) : AiTool {
    override val name = "createQuestsBatch"
    override val description = "Create multiple quests at once."
    override val parametersSchema = """{"quests":"array of {title, schedule, nagIntervalMinutes?, maxNags?} (required)"}"""
    override val isWriteTool = true

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        @Suppress("UNCHECKED_CAST")
        val quests = (args["quests"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
            ?: return ToolResult.Error("Missing required parameter: quests (array)")
        return try {
            val created = quests.map { q ->
                val title = q["title"] as? String ?: return ToolResult.Error("Quest missing title")
                val schedule = q["schedule"] as? String ?: return ToolResult.Error("Quest missing schedule")
                val nagInterval = (q["nagIntervalMinutes"] as? Number)?.toInt() ?: 5
                val maxNags = (q["maxNags"] as? Number)?.toInt() ?: 100
                reminderRepo.create(title, schedule, nagInterval, maxNags)
            }
            val ids = created.joinToString(",") { """"${it.id}"""" }
            ToolResult.Success("""{"created":[$ids],"count":${created.size}}""")
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Failed to create quests batch")
        }
    }
}

class UpdateQuestsBatchTool(private val reminderRepo: ReminderRepository) : AiTool {
    override val name = "updateQuestsBatch"
    override val description = "Update multiple quests at once."
    override val parametersSchema = """{"updates":"array of {id, title?, schedule?, nagIntervalMinutes?, maxNags?, enabled?} (required)"}"""
    override val isWriteTool = true

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        @Suppress("UNCHECKED_CAST")
        val updates = (args["updates"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
            ?: return ToolResult.Error("Missing required parameter: updates (array)")
        return try {
            var count = 0
            updates.forEach { u ->
                val id = u["id"] as? String ?: return ToolResult.Error("Update missing id")
                val existing = reminderRepo.getById(id) ?: return ToolResult.Error("Quest not found: $id")
                val updated = existing.copy(
                    title = u["title"] as? String ?: existing.title,
                    schedule = u["schedule"] as? String ?: existing.schedule,
                    nagIntervalMinutes = (u["nagIntervalMinutes"] as? Number)?.toInt() ?: existing.nagIntervalMinutes,
                    maxNags = (u["maxNags"] as? Number)?.toInt() ?: existing.maxNags,
                    enabled = u["enabled"] as? Boolean ?: existing.enabled
                )
                reminderRepo.update(updated)
                count++
            }
            ToolResult.Success("""{"updated":$count}""")
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Failed to update quests batch")
        }
    }
}

class DeleteQuestsBatchTool(private val reminderRepo: ReminderRepository) : AiTool {
    override val name = "deleteQuestsBatch"
    override val description = "Delete multiple quests by their IDs."
    override val parametersSchema = """{"ids":"array of strings (required) - quest IDs to delete"}"""
    override val isWriteTool = true

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        @Suppress("UNCHECKED_CAST")
        val ids = (args["ids"] as? List<*>)?.filterIsInstance<String>()
            ?: return ToolResult.Error("Missing required parameter: ids (array)")
        return try {
            ids.forEach { reminderRepo.delete(it) }
            ToolResult.Success("""{"deleted":${ids.size}}""")
        } catch (e: Exception) {
            ToolResult.Error(e.message ?: "Failed to delete quests batch")
        }
    }
}
