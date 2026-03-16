package org.nudgealarm.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.nudgealarm.app.ai.tools.AiTool
import org.nudgealarm.app.ai.tools.ToolRegistry
import org.nudgealarm.app.ai.tools.ToolResult

class ToolRegistryTest {

    private lateinit var registry: ToolRegistry

    private fun makeTool(toolName: String, write: Boolean = false) = object : AiTool {
        override val name = toolName
        override val description = "Test tool: $toolName"
        override val parametersSchema = "{}"
        override val isWriteTool = write
        override suspend fun execute(args: Map<String, Any?>) = ToolResult.Success("ok")
    }

    @Before
    fun setUp() {
        registry = ToolRegistry()
    }

    @Test
    fun `find returns null for empty registry`() {
        assertNull(registry.find("anything"))
    }

    @Test
    fun `registered tool can be found by name`() {
        registry.register(makeTool("myTool"))
        assertNotNull(registry.find("myTool"))
        assertEquals("myTool", registry.find("myTool")!!.name)
    }

    @Test
    fun `find returns null for unregistered name`() {
        registry.register(makeTool("toolA"))
        assertNull(registry.find("toolB"))
    }

    @Test
    fun `listAll returns all registered tools`() {
        registry.register(makeTool("toolA"))
        registry.register(makeTool("toolB"))
        registry.register(makeTool("toolC"))
        assertEquals(3, registry.listAll().size)
    }

    @Test
    fun `listReadTools filters write tools`() {
        registry.register(makeTool("readA", write = false))
        registry.register(makeTool("writeA", write = true))
        registry.register(makeTool("readB", write = false))
        val reads = registry.listReadTools()
        assertEquals(2, reads.size)
        assertTrue(reads.none { it.isWriteTool })
    }

    @Test
    fun `listWriteTools filters read tools`() {
        registry.register(makeTool("readA", write = false))
        registry.register(makeTool("writeA", write = true))
        registry.register(makeTool("writeB", write = true))
        val writes = registry.listWriteTools()
        assertEquals(2, writes.size)
        assertTrue(writes.all { it.isWriteTool })
    }

    @Test
    fun `registering same name twice overwrites`() {
        val first = makeTool("tool")
        val second = object : AiTool {
            override val name = "tool"
            override val description = "second"
            override val parametersSchema = "{}"
            override val isWriteTool = false
            override suspend fun execute(args: Map<String, Any?>) = ToolResult.Success("second")
        }
        registry.register(first)
        registry.register(second)
        assertEquals("second", registry.find("tool")!!.description)
        assertEquals(1, registry.listAll().size)
    }
}
