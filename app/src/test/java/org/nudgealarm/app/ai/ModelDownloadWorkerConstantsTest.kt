package org.nudgealarm.app.ai

import org.junit.Assert.assertTrue
import org.junit.Test
import org.nudgealarm.app.ai.model.ModelDownloadWorker

/** Verifies constants and lightweight logic in ModelDownloadWorker. */
class ModelDownloadWorkerConstantsTest {

    @Test
    fun `minimum valid size is at least 50MB`() {
        assertTrue(ModelDownloadWorker.MIN_VALID_SIZE_BYTES >= 50 * 1024 * 1024L)
    }

    @Test
    fun `minimum valid size rejects typical HTML error responses`() {
        // A HuggingFace 403 JSON error body is typically < 1KB
        val typicalAuthErrorSize = 512L
        assertTrue(typicalAuthErrorSize < ModelDownloadWorker.MIN_VALID_SIZE_BYTES)
    }

    @Test
    fun `minimum valid size accepts 100MB file`() {
        val hundredMb = 100 * 1024 * 1024L
        assertTrue(hundredMb >= ModelDownloadWorker.MIN_VALID_SIZE_BYTES)
    }
}
