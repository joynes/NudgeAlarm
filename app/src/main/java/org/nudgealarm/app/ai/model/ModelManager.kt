package org.nudgealarm.app.ai.model

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nudgealarm.app.ai.engine.LlmEngine
import org.nudgealarm.app.ai.engine.MediaPipeLlmEngine
import java.io.File

private const val INACTIVITY_UNLOAD_MS = 5 * 60 * 1000L // 5 minutes

class ModelManager(private val context: Context) {

    private val modelsDir: File
        get() = File(context.filesDir, "ai_models").also { it.mkdirs() }

    private var loadedEngine: LlmEngine? = null
    private var loadedModelId: String? = null
    private var inactivityJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun getModelFile(model: ModelInfo): File = File(modelsDir, model.filename)

    /**
     * A model is "installed" only if the file exists AND is large enough to be a real model binary.
     * A corrupt download (e.g. a 403 HTML page) will be smaller than MIN_VALID_SIZE_BYTES.
     */
    fun isInstalled(model: ModelInfo): Boolean {
        val file = getModelFile(model)
        return file.exists() && file.length() >= ModelDownloadWorker.MIN_VALID_SIZE_BYTES
    }

    /** Delete a model file (e.g. if it is corrupt). */
    fun deleteModel(model: ModelInfo) {
        getModelFile(model).delete()
        File(modelsDir, "${model.filename}.download").delete()
        if (loadedModelId == model.id) {
            unloadEngine()
        }
    }

    fun installedModels(): List<ModelInfo> = ModelCatalog.models.filter { isInstalled(it) }

    /**
     * Load the specified model into an LlmEngine.
     * Returns the engine (initializes MediaPipe on first call or if model changed).
     */
    fun loadModel(model: ModelInfo): LlmEngine {
        if (loadedModelId == model.id && loadedEngine != null) {
            resetInactivityTimer()
            return loadedEngine!!
        }

        // Unload previous engine
        loadedEngine?.close()
        loadedEngine = null
        loadedModelId = null

        val modelFile = getModelFile(model)
        require(modelFile.exists()) { "Model file not found: ${modelFile.absolutePath}" }
        require(modelFile.length() >= ModelDownloadWorker.MIN_VALID_SIZE_BYTES) {
            "Model file appears corrupt (${modelFile.length() / 1024} KB). " +
            "This is likely an authentication error — the file contains an error page instead of the model. " +
            "Please delete and re-download with a valid HuggingFace token."
        }

        val engine = MediaPipeLlmEngine(
            context = context,
            modelPath = modelFile.absolutePath
        )
        try {
            engine.initialize()
        } catch (t: Throwable) {
            throw RuntimeException("MediaPipe init failed: ${t.message}", t)
        }
        loadedEngine = engine
        loadedModelId = model.id

        resetInactivityTimer()
        return engine
    }

    fun unloadEngine() {
        inactivityJob?.cancel()
        loadedEngine?.close()
        loadedEngine = null
        loadedModelId = null
    }

    fun notifyActivity() {
        resetInactivityTimer()
    }

    private fun resetInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = scope.launch {
            delay(INACTIVITY_UNLOAD_MS)
            unloadEngine()
        }
    }
}
