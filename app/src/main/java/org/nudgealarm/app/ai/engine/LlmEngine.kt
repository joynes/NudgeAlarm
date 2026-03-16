package org.nudgealarm.app.ai.engine

interface LlmEngine {
    /**
     * Generate a response for the given prompt.
     * [onToken] is called with each new token/chunk as it arrives.
     * [onComplete] is called with the full final text when done.
     * [onError] is called if generation fails.
     */
    suspend fun generateResponse(
        prompt: String,
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Exception) -> Unit
    )

    fun close()
}
