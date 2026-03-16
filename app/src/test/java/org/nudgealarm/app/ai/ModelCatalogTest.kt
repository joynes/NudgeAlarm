package org.nudgealarm.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nudgealarm.app.ai.model.ModelCatalog

class ModelCatalogTest {

    @Test
    fun `catalog has at least one model`() {
        assertTrue(ModelCatalog.models.isNotEmpty())
    }

    @Test
    fun `all models have non-blank fields`() {
        ModelCatalog.models.forEach { model ->
            assertTrue("ID blank for ${model.name}", model.id.isNotBlank())
            assertTrue("Name blank for ${model.id}", model.name.isNotBlank())
            assertTrue("Description blank for ${model.id}", model.description.isNotBlank())
            assertTrue("URL blank for ${model.id}", model.huggingFaceUrl.isNotBlank())
            assertTrue("Filename blank for ${model.id}", model.filename.isNotBlank())
            assertTrue("Size must be positive for ${model.id}", model.sizeBytes > 0)
        }
    }

    @Test
    fun `all model IDs are unique`() {
        val ids = ModelCatalog.models.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `all model filenames are unique`() {
        val filenames = ModelCatalog.models.map { it.filename }
        assertEquals(filenames.size, filenames.distinct().size)
    }

    @Test
    fun `findById returns correct model`() {
        ModelCatalog.models.forEach { model ->
            val found = ModelCatalog.findById(model.id)
            assertNotNull("Should find model ${model.id}", found)
            assertEquals(model.id, found!!.id)
        }
    }

    @Test
    fun `findById returns null for unknown id`() {
        assertNull(ModelCatalog.findById("definitely_not_a_real_model_id"))
    }

    @Test
    fun `no GGUF models in catalog (MediaPipe incompatible)`() {
        ModelCatalog.models.forEach { model ->
            assertFalse(
                "Model ${model.id} uses GGUF which is incompatible with MediaPipe: ${model.filename}",
                model.filename.endsWith(".gguf")
            )
        }
    }

    @Test
    fun `all model URLs start with https`() {
        ModelCatalog.models.forEach { model ->
            assertTrue(
                "URL for ${model.id} should use HTTPS: ${model.huggingFaceUrl}",
                model.huggingFaceUrl.startsWith("https://")
            )
        }
    }

    @Test
    fun `sizeMb is reasonable (between 100MB and 10GB)`() {
        ModelCatalog.models.forEach { model ->
            assertTrue("Model ${model.id} sizeMb=${model.sizeMb} too small", model.sizeMb >= 100)
            assertTrue("Model ${model.id} sizeMb=${model.sizeMb} unreasonably large", model.sizeMb <= 10_000)
        }
    }
}
