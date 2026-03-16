package org.nudgealarm.app.ai.engine

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Wraps the MediaPipe LlmInference API (tasks-genai 0.10.32).
 *
 * API changes from 0.10.14:
 * - No result/error listener on options builder
 * - generateResponseAsync(prompt, ProgressListener) takes a per-call listener
 * - Returns ListenableFuture<String>; errors surface via the future
 * - setTopK/setTemperature/setRandomSeed removed; use setMaxTopK
 */
class MediaPipeLlmEngine(
    private val context: Context,
    private val modelPath: String,
    private val maxTokens: Int = 2048,
    private val maxTopK: Int = 40
) : LlmEngine {

    private val generationMutex = Mutex()
    private var llmInference: LlmInference? = null

    // Shared single-thread executor for future error callbacks
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    fun initialize() {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(maxTokens)
            .setMaxTopK(maxTopK)
            .build()
        llmInference = LlmInference.createFromOptions(context, options)
    }

    override suspend fun generateResponse(
        prompt: String,
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val inference = llmInference ?: run {
            onError(IllegalStateException("LLM engine not initialized"))
            return
        }

        generationMutex.withLock {
            suspendCancellableCoroutine<Unit> { cont ->
                // MediaPipe 0.10.32 delivers individual token fragments per callback,
                // NOT accumulated text. Each non-done callback is a new piece to append.
                var accumulated = ""
                var done = false

                val listener = ProgressListener<String> { partialResult, isDone ->
                    val text = partialResult ?: ""
                    if (!isDone) {
                        if (text.isNotEmpty()) {
                            accumulated += text
                            onToken(text)
                        }
                    } else {
                        // isDone=true delivers the final token fragment (may be empty)
                        if (text.isNotEmpty()) {
                            accumulated += text
                            onToken(text)
                        }
                        done = true
                        onComplete(accumulated)
                        if (cont.isActive) cont.resume(Unit)
                    }
                }

                try {
                    val future = inference.generateResponseAsync(prompt, listener)

                    // Catch errors that surface via the future (e.g. native exceptions)
                    future.addListener({
                        if (!done) {
                            try {
                                future.get()
                            } catch (e: ExecutionException) {
                                val cause = e.cause
                                val ex = when {
                                    cause is Exception -> cause
                                    cause != null -> RuntimeException(cause)
                                    else -> e
                                }
                                onError(ex)
                                if (cont.isActive) cont.resume(Unit)
                            } catch (t: Throwable) {
                                onError(RuntimeException(t))
                                if (cont.isActive) cont.resume(Unit)
                            }
                        }
                    }, callbackExecutor)

                } catch (t: Throwable) {
                    onError(if (t is Exception) t else RuntimeException(t))
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    override fun close() {
        llmInference?.close()
        llmInference = null
        callbackExecutor.shutdown()
    }
}
