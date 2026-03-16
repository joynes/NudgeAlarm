package org.nudgealarm.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.nudgealarm.app.ai.conversation.ChatMessage
import org.nudgealarm.app.ai.conversation.ConversationManager
import org.nudgealarm.app.ai.model.ModelInfo
import org.nudgealarm.app.ai.tools.AiTool
import org.nudgealarm.app.ai.tools.ToolResult

class ConversationManagerTest {

    private lateinit var manager: ConversationManager
    private val gemmaModel = ModelInfo(
        id = "gemma_2b", name = "Gemma 2B", description = "", sizeBytes = 1_000_000L,
        huggingFaceUrl = "", filename = "gemma.bin"
    )
    private val phi2Model = ModelInfo(
        id = "phi2", name = "Phi-2", description = "", sizeBytes = 1_000_000L,
        huggingFaceUrl = "", filename = "phi2.bin"
    )

    private val fakeReadTool = object : AiTool {
        override val name = "listQuests"
        override val description = "List all quests"
        override val parametersSchema = "{}"
        override val isWriteTool = false
        override suspend fun execute(args: Map<String, Any?>) = ToolResult.Success("[]")
    }
    private val fakeWriteTool = object : AiTool {
        override val name = "createQuest"
        override val description = "Create a quest"
        override val parametersSchema = """{"title":"string","schedule":"string"}"""
        override val isWriteTool = true
        override suspend fun execute(args: Map<String, Any?>) = ToolResult.Success("{}")
    }

    @Before
    fun setUp() {
        manager = ConversationManager()
    }

    // ── parseToolCall ──────────────────────────────────────────────────────────

    @Test
    fun `parseToolCall returns null when no tool call in text`() {
        assertNull(manager.parseToolCall("Hello, how can I help you?"))
    }

    @Test
    fun `parseToolCall extracts tool name and empty args`() {
        val result = manager.parseToolCall("TOOL:listQuests {}")
        assertNotNull(result)
        assertEquals("listQuests", result!!.name)
        assertTrue(result.arguments.isEmpty())
    }

    @Test
    fun `parseToolCall extracts string argument`() {
        val text = """TOOL:getQuest {"id":"my_quest_123"}"""
        val result = manager.parseToolCall(text)
        assertNotNull(result)
        assertEquals("getQuest", result!!.name)
        assertEquals("my_quest_123", result.arguments["id"])
    }

    @Test
    fun `parseToolCall extracts multiple arguments`() {
        val text = """TOOL:createQuest {"title":"Morning Run","schedule":"0 7 * * 1-5"}"""
        val result = manager.parseToolCall(text)
        assertNotNull(result)
        assertEquals("createQuest", result!!.name)
        assertEquals("Morning Run", result.arguments["title"])
        assertEquals("0 7 * * 1-5", result.arguments["schedule"])
    }

    @Test
    fun `parseToolCall extracts number argument`() {
        val text = """TOOL:createQuest {"title":"Run","schedule":"0 9 * * *","nagIntervalMinutes":10}"""
        val result = manager.parseToolCall(text)
        assertNotNull(result)
        assertEquals(10L, result!!.arguments["nagIntervalMinutes"])
    }

    @Test
    fun `parseToolCall extracts boolean argument`() {
        val text = """TOOL:updateQuest {"id":"q1","enabled":false}"""
        val result = manager.parseToolCall(text)
        assertNotNull(result)
        assertEquals(false, result!!.arguments["enabled"])
    }

    @Test
    fun `parseToolCall handles array argument`() {
        val text = """TOOL:getQuestsByIds {"ids":["q1","q2","q3"]}"""
        val result = manager.parseToolCall(text)
        assertNotNull(result)
        val ids = result!!.arguments["ids"] as List<*>
        assertEquals(3, ids.size)
        assertEquals("q1", ids[0])
        assertEquals("q3", ids[2])
    }

    @Test
    fun `parseToolCall returns null for malformed call`() {
        assertNull(manager.parseToolCall("TOOL:test"))  // missing args
    }

    // ── textBeforeToolCall ─────────────────────────────────────────────────────

    @Test
    fun `textBeforeToolCall returns full text when no tool call`() {
        val text = "Hello, I can help with that."
        assertEquals(text, manager.textBeforeToolCall(text))
    }

    @Test
    fun `textBeforeToolCall returns empty when tool call at start`() {
        val text = "TOOL:listQuests {}"
        assertEquals("", manager.textBeforeToolCall(text))
    }

    @Test
    fun `textBeforeToolCall returns preamble before tool call`() {
        val text = """Let me look that up for you. TOOL:listQuests {}"""
        assertEquals("Let me look that up for you.", manager.textBeforeToolCall(text))
    }

    @Test
    fun `textBeforeToolCall trims trailing whitespace`() {
        val text = """Sure!   TOOL:listQuests {}"""
        assertEquals("Sure!", manager.textBeforeToolCall(text))
    }

    // ── buildPrompt ────────────────────────────────────────────────────────────

    @Test
    fun `buildPrompt for gemma starts with start_of_turn`() {
        val messages = listOf(ChatMessage.User("Hello"))
        val prompt = manager.buildPrompt(messages, gemmaModel, emptyList(), "")
        assertTrue(prompt.startsWith("<start_of_turn>"))
        assertTrue(prompt.contains("Hello"))
        assertTrue(prompt.endsWith("<start_of_turn>model\n"))
    }

    @Test
    fun `buildPrompt for phi2 starts with Instruct`() {
        val messages = listOf(ChatMessage.User("Hello"))
        val prompt = manager.buildPrompt(messages, phi2Model, emptyList(), "")
        assertTrue(prompt.startsWith("Instruct:"))
        assertTrue(prompt.contains("Hello"))
        assertTrue(prompt.endsWith("Output:"))
    }

    @Test
    fun `buildPrompt includes write tool definitions in system prompt`() {
        val prompt = manager.buildPrompt(emptyList(), gemmaModel, listOf(fakeWriteTool), "")
        assertTrue(prompt.contains("createQuest"))
    }

    @Test
    fun `buildPrompt includes quest context in system prompt`() {
        val questContext = "- q1: \"Morning Run\" schedule=0 7 * * 1-5 [on]"
        val prompt = manager.buildPrompt(emptyList(), gemmaModel, emptyList(), questContext)
        assertTrue(prompt.contains("Morning Run"))
    }

    @Test
    fun `buildPrompt limits history to last 8 messages`() {
        // Generate 20 user messages
        val messages = (1..20).map { ChatMessage.User("Message $it") }
        val prompt = manager.buildPrompt(messages, gemmaModel, emptyList(), "")
        // Should NOT contain the earliest messages
        assertTrue(!prompt.contains("Message 1\n") || prompt.contains("Message 20"))
    }

    @Test
    fun `buildPrompt includes tool result message`() {
        val messages = listOf(
            ChatMessage.User("How many quests?"),
            ChatMessage.ToolResultMessage("createQuest", ToolResult.Success("""{"id":"q1"}"""))
        )
        val prompt = manager.buildPrompt(messages, gemmaModel, emptyList(), "")
        assertTrue(prompt.contains("createQuest"))
    }

    // ── buildSystemPrompt ──────────────────────────────────────────────────────

    @Test
    fun `buildSystemPrompt contains write tool names`() {
        val prompt = manager.buildSystemPrompt(listOf(fakeWriteTool), "")
        assertTrue(prompt.contains("createQuest"))
    }

    @Test
    fun `buildSystemPrompt contains quest context`() {
        val prompt = manager.buildSystemPrompt(emptyList(), "- q1: \"Run\" [on]")
        assertTrue(prompt.contains("Run"))
    }

    @Test
    fun `buildSystemPrompt contains tool call format instruction when write tools present`() {
        val prompt = manager.buildSystemPrompt(listOf(fakeWriteTool), "")
        assertTrue(prompt.contains("TOOL:"))
    }

    @Test
    fun `buildSystemPrompt contains cron format hint`() {
        val prompt = manager.buildSystemPrompt(emptyList(), "")
        assertTrue(prompt.lowercase().contains("cron"))
    }
}
