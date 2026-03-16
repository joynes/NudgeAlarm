package org.nudgealarm.app.ai

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.nudgealarm.app.ai.conversation.ChatMessage
import org.nudgealarm.app.ai.conversation.ConfirmationState
import org.nudgealarm.app.ai.conversation.ConversationManager
import org.nudgealarm.app.ai.model.DownloadState
import org.nudgealarm.app.ai.model.HuggingFaceApi
import org.nudgealarm.app.ai.model.ModelCatalog
import org.nudgealarm.app.ai.model.ModelDownloader
import org.nudgealarm.app.ai.model.ModelInfo
import org.nudgealarm.app.ai.model.ModelManager
import org.nudgealarm.app.ai.tools.CreateQuestTool
import org.nudgealarm.app.ai.tools.CreateQuestsBatchTool
import org.nudgealarm.app.ai.tools.DeleteQuestTool
import org.nudgealarm.app.ai.tools.DeleteQuestsBatchTool
import org.nudgealarm.app.ai.tools.GetQuestTool
import org.nudgealarm.app.ai.tools.GetQuestsByIdsTool
import org.nudgealarm.app.ai.tools.ListActiveQuestsTool
import org.nudgealarm.app.ai.tools.ListQuestsTool
import org.nudgealarm.app.ai.tools.SearchQuestsTool
import org.nudgealarm.app.ai.tools.ToolCall
import org.nudgealarm.app.ai.tools.ToolRegistry
import org.nudgealarm.app.ai.tools.ToolResult
import org.nudgealarm.app.ai.tools.UpdateQuestTool
import org.nudgealarm.app.ai.tools.UpdateQuestsBatchTool
import org.nudgealarm.app.core.event.Event
import org.nudgealarm.app.database.NagDatabase
import org.nudgealarm.app.database.NagRepository
import org.nudgealarm.app.database.ReminderRepository
import org.nudgealarm.app.service.ReminderService
import org.nudgealarm.app.storage.EventLogStore

data class AiUiState(
    val isSheetVisible: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val selectedModelId: String? = null,
    val downloadStates: Map<String, DownloadState> = emptyMap(),
    val isGenerating: Boolean = false,
    val error: String? = null,
    val showModelDownloadScreen: Boolean = false,
    val hfToken: String = "",
    // Remote model browser
    val remoteModels: List<org.nudgealarm.app.ai.model.ModelInfo> = emptyList(),
    val isLoadingRemoteModels: Boolean = false,
    val remoteModelsError: String? = null,
    val modelSearchQuery: String = ""
)

class AiViewModel(application: Application) : AndroidViewModel(application) {

    private val db = NagDatabase.getInstance(application)
    private val reminderRepo = ReminderRepository(db.reminderDao())
    private val nagRepo = NagRepository(db.nagStateDao())

    private val toolRegistry = ToolRegistry().also { registry ->
        registry.register(ListQuestsTool(reminderRepo))
        registry.register(GetQuestTool(reminderRepo))
        registry.register(SearchQuestsTool(reminderRepo))
        registry.register(ListActiveQuestsTool(nagRepo))
        registry.register(GetQuestsByIdsTool(reminderRepo))
        registry.register(CreateQuestTool(reminderRepo))
        registry.register(UpdateQuestTool(reminderRepo))
        registry.register(DeleteQuestTool(reminderRepo))
        registry.register(CreateQuestsBatchTool(reminderRepo))
        registry.register(UpdateQuestsBatchTool(reminderRepo))
        registry.register(DeleteQuestsBatchTool(reminderRepo))
    }

    private val conversationManager = ConversationManager()
    private val modelManager = ModelManager(application)
    private val modelDownloader = ModelDownloader(application)
    private val historyMutex = Mutex()

    private val _uiState = MutableStateFlow(AiUiState())
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    init {
        // Observe download states for all catalog models
        ModelCatalog.models.forEach { model ->
            viewModelScope.launch {
                modelDownloader.getDownloadState(model.id).collect { state ->
                    _uiState.update { it.copy(downloadStates = it.downloadStates + (model.id to state)) }

                    // Log notable state transitions
                    val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                        .format(java.util.Date())
                    when (state) {
                        is DownloadState.Success -> {
                            EventLogStore(getApplication()).add(Event.Debug(detail = "AI download complete: ${model.name}"))
                        }
                        is DownloadState.Failed -> {
                            Log.e("AiViewModel", "Download failed for ${model.id}: ${state.error}")
                            EventLogStore(getApplication()).add(Event.Debug(detail = "AI download FAILED: ${model.name}: ${state.error}"))
                        }
                        else -> {}
                    }

                    // Auto-select newly downloaded model if none selected
                    if (state is DownloadState.Success && _uiState.value.selectedModelId == null) {
                        _uiState.update { it.copy(selectedModelId = model.id) }
                    }
                }
            }
        }

        // Auto-select first installed model
        val installed = modelManager.installedModels()
        if (installed.isNotEmpty()) {
            _uiState.update { it.copy(selectedModelId = installed.first().id) }
        }
    }

    fun showSheet() {
        _uiState.update { it.copy(isSheetVisible = true) }
        modelManager.notifyActivity()
    }

    fun hideSheet() {
        _uiState.update { it.copy(isSheetVisible = false) }
    }

    fun setInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun selectModel(modelId: String) {
        _uiState.update { it.copy(selectedModelId = modelId) }
    }

    fun showModelDownloadScreen() {
        _uiState.update { it.copy(showModelDownloadScreen = true) }
    }

    fun hideModelDownloadScreen() {
        _uiState.update { it.copy(showModelDownloadScreen = false) }
    }

    fun setHfToken(token: String) {
        _uiState.update { it.copy(hfToken = token) }
    }

    fun startDownload(model: ModelInfo) {
        val token = _uiState.value.hfToken.trim().takeIf { it.isNotEmpty() }
        val tokenStatus = if (token != null) "with token" else "no token"
        Log.d("AiViewModel", "Starting download: ${model.id} from ${model.huggingFaceUrl} ($tokenStatus)")
        EventLogStore(getApplication()).add(Event.Debug(detail = "AI download started: ${model.name} ($tokenStatus)"))
        modelDownloader.startDownload(model, hfToken = token)
    }

    fun fetchRemoteModels() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingRemoteModels = true, remoteModelsError = null) }
            val token = _uiState.value.hfToken.trim().takeIf { it.isNotEmpty() }
            val models = HuggingFaceApi.fetchLiteRtModels(token)
            _uiState.update {
                it.copy(
                    remoteModels = models,
                    isLoadingRemoteModels = false,
                    remoteModelsError = if (models.isEmpty()) "No models found. Check your token." else null
                )
            }
        }
    }

    fun searchRemoteModels(query: String) {
        _uiState.update { it.copy(modelSearchQuery = query) }
        if (query.length < 2) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingRemoteModels = true, remoteModelsError = null) }
            val token = _uiState.value.hfToken.trim().takeIf { it.isNotEmpty() }
            val models = HuggingFaceApi.searchModels(query, token)
            _uiState.update {
                it.copy(
                    remoteModels = models,
                    isLoadingRemoteModels = false,
                    remoteModelsError = if (models.isEmpty()) "No results for \"$query\"." else null
                )
            }
        }
    }

    fun cancelDownload(modelId: String) {
        modelDownloader.cancelDownload(modelId)
    }

    fun deleteModel(model: ModelInfo) {
        modelManager.deleteModel(model)
        // Refresh installed model list by triggering a state update
        _uiState.update { it.copy(error = null) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return
        val modelId = _uiState.value.selectedModelId ?: run {
            _uiState.update { it.copy(error = "No model selected. Please download a model first.") }
            return
        }
        val modelInfo = ModelCatalog.findById(modelId) ?: run {
            _uiState.update { it.copy(error = "Unknown model: $modelId") }
            return
        }

        _uiState.update { it.copy(inputText = "", isGenerating = true, error = null) }

        viewModelScope.launch {
            try {
                historyMutex.withLock {
                    appendMessage(ChatMessage.User(text))
                }
                generateAiResponse(modelInfo)
            } catch (e: Exception) {
                logException("sendMessage", e)
                _uiState.update { it.copy(isGenerating = false, error = "Error: ${e.message}") }
            }
        }
    }

    fun confirmToolCall(messageId: String) {
        viewModelScope.launch {
            val message = _uiState.value.messages
                .filterIsInstance<ChatMessage.ToolCallMessage>()
                .find { it.id == messageId } ?: return@launch

            // Mark as confirmed in UI
            historyMutex.withLock {
                updateToolCallConfirmation(messageId, ConfirmationState.CONFIRMED)
            }

            // Execute the tool
            val tool = toolRegistry.find(message.toolCall.name)
            val result = if (tool != null) {
                tool.execute(message.toolCall.arguments)
            } else {
                ToolResult.Error("Unknown tool: ${message.toolCall.name}")
            }
            EventLogStore(getApplication()).add(Event.Debug(
                detail = "WRITE TOOL RESULT ${message.toolCall.name}: ${result.text.take(300)}"
            ))

            historyMutex.withLock {
                appendMessage(ChatMessage.ToolResultMessage(message.toolCall.name, result))
            }

            // Trigger service reload if write tool succeeded
            if (tool?.isWriteTool == true && result is ToolResult.Success) {
                reloadService()
            }

            // Continue conversation with the result
            val modelId = _uiState.value.selectedModelId ?: return@launch
            val modelInfo = ModelCatalog.findById(modelId) ?: return@launch
            _uiState.update { it.copy(isGenerating = true) }
            generateAiResponse(modelInfo)
        }
    }

    fun denyToolCall(messageId: String) {
        viewModelScope.launch {
            historyMutex.withLock {
                updateToolCallConfirmation(messageId, ConfirmationState.DENIED)
                appendMessage(ChatMessage.SystemMessage("User denied the tool call."))
            }

            val modelId = _uiState.value.selectedModelId ?: return@launch
            val modelInfo = ModelCatalog.findById(modelId) ?: return@launch
            _uiState.update { it.copy(isGenerating = true) }
            generateAiResponse(modelInfo)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearConversation() {
        viewModelScope.launch {
            historyMutex.withLock {
                _uiState.update { it.copy(messages = emptyList()) }
            }
        }
    }

    private suspend fun generateAiResponse(modelInfo: ModelInfo) {
        // LlmInference.createFromOptions() loads a ~600MB model and initializes native sessions —
        // must run on IO thread, never the main thread.
        val engine = try {
            withContext(Dispatchers.IO) { modelManager.loadModel(modelInfo) }
        } catch (e: Exception) {
            logException("loadModel", e)
            _uiState.update { it.copy(isGenerating = false, error = "Failed to load model: ${e.message}") }
            return
        }

        val currentMessages = historyMutex.withLock { _uiState.value.messages.toList() }
        val writeTools = toolRegistry.listAll().filter { it.isWriteTool }
        val questContext = withContext(Dispatchers.IO) { buildQuestContext() }
        val prompt = conversationManager.buildPrompt(currentMessages, modelInfo, writeTools, questContext)

        // Log prompt summary to EventLog (visible in Debug Log menu)
        val userMsg = currentMessages.filterIsInstance<ChatMessage.User>().lastOrNull()?.content?.take(80) ?: "(no user message)"
        EventLogStore(getApplication()).add(Event.Debug(detail = "AI prompt sent to ${modelInfo.id} | last user msg: $userMsg | prompt size: ${prompt.length} chars"))

        // Add a streaming assistant message placeholder
        val streamingMsgId = java.util.UUID.randomUUID().toString()
        historyMutex.withLock {
            appendMessage(ChatMessage.Assistant("", isStreaming = true, id = streamingMsgId))
        }

        var fullText = ""

        engine.generateResponse(
            prompt = prompt,
            onToken = { token ->
                fullText += token
                // Show streaming text with special tokens stripped for readability
                val display = conversationManager.postProcessResponse(fullText)
                _uiState.update { state ->
                    state.copy(messages = state.messages.map { msg ->
                        if (msg is ChatMessage.Assistant && msg.id == streamingMsgId) {
                            msg.copy(content = display)
                        } else msg
                    })
                }
            },
            onComplete = { finalText ->
                // MediaPipe 0.10.32 onComplete delivers only the LAST TOKEN, not the full text.
                // Prefer the accumulated fullText from onToken if it is longer.
                val completeText = if (fullText.length >= finalText.length) fullText else finalText
                EventLogStore(getApplication()).add(Event.Debug(
                    detail = "AI onComplete: final=${finalText.length}c, accumulated=${fullText.length}c → using ${completeText.length}c: ${completeText.take(200)}"
                ))
                fullText = completeText
                viewModelScope.launch {
                    processCompletedResponse(streamingMsgId, completeText, modelInfo)
                }
            },
            onError = { e ->
                logException("generateResponse", e)
                viewModelScope.launch {
                    historyMutex.withLock {
                        // Remove streaming placeholder
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages.filter { it.id != streamingMsgId },
                                isGenerating = false,
                                error = "Generation error: ${e.message}"
                            )
                        }
                    }
                }
            }
        )
    }

    private suspend fun processCompletedResponse(
        streamingMsgId: String,
        fullText: String,
        modelInfo: ModelInfo
    ) {
        val cleanText = conversationManager.postProcessResponse(fullText)
        // Log raw response so tool call issues are visible in Debug Log
        EventLogStore(getApplication()).add(Event.Debug(
            detail = "AI raw response (${cleanText.length}c): ${cleanText.take(300)}"
        ))
        val toolCall = conversationManager.parseToolCall(cleanText)
        val textBefore = conversationManager.textBeforeToolCall(cleanText)
        if (toolCall != null) {
            EventLogStore(getApplication()).add(Event.Debug(
                detail = "TOOL CALL → ${toolCall.name}(${toolCall.arguments})"
            ))
        } else if (cleanText.contains("TOOL:")) {
            EventLogStore(getApplication()).add(Event.Debug(
                detail = "TOOL CALL PARSE FAILED — raw had 'TOOL:' but regex didn't match: ${cleanText.take(200)}"
            ))
        }

        historyMutex.withLock {
            if (toolCall != null) {
                // Replace streaming message with text-before (if any)
                val assistantContent = textBefore
                _uiState.update { state ->
                    val msgs = state.messages.toMutableList()
                    val idx = msgs.indexOfFirst { it.id == streamingMsgId }
                    if (idx >= 0) {
                        if (assistantContent.isNotBlank()) {
                            msgs[idx] = ChatMessage.Assistant(assistantContent, isStreaming = false, id = streamingMsgId)
                        } else {
                            msgs.removeAt(idx)
                        }
                    }
                    state.copy(messages = msgs)
                }
                appendMessage(ChatMessage.ToolCallMessage(toolCall))
            } else {
                // Finalize the streaming message; show placeholder if response was empty
                val displayText = cleanText.ifBlank { "(no response)" }
                _uiState.update { state ->
                    state.copy(messages = state.messages.map { msg ->
                        if (msg is ChatMessage.Assistant && msg.id == streamingMsgId) {
                            msg.copy(content = displayText, isStreaming = false)
                        } else msg
                    })
                }
            }
        }

        if (toolCall != null) {
            val tool = toolRegistry.find(toolCall.name)
            if (tool == null) {
                // Unknown tool – inject error and continue
                historyMutex.withLock {
                    appendMessage(ChatMessage.ToolResultMessage(toolCall.name, ToolResult.Error("Unknown tool: ${toolCall.name}")))
                }
                _uiState.update { it.copy(isGenerating = true) }
                generateAiResponse(modelInfo)
            } else if (!tool.isWriteTool) {
                // Read tool – execute immediately
                val result = tool.execute(toolCall.arguments)
                EventLogStore(getApplication()).add(Event.Debug(
                    detail = "TOOL RESULT ${toolCall.name}: ${result.text.take(300)}"
                ))
                historyMutex.withLock {
                    appendMessage(ChatMessage.ToolResultMessage(toolCall.name, result))
                }
                _uiState.update { it.copy(isGenerating = true) }
                generateAiResponse(modelInfo)
            } else {
                // Write tool – show confirmation card, stop generating
                _uiState.update { it.copy(isGenerating = false) }
            }
        } else {
            _uiState.update { it.copy(isGenerating = false) }
        }
    }

    /** Builds a compact quest list to inject into every system prompt (read from DB). */
    private suspend fun buildQuestContext(): String {
        return try {
            val reminders = reminderRepo.getAllReminders()
            if (reminders.isEmpty()) return "(no quests yet)"
            reminders.take(30).joinToString("\n") { r ->
                val enabledTag = if (r.enabled) "on" else "off"
                "- ${r.id}: \"${r.title}\" schedule=${r.schedule} [$enabledTag]"
            }
        } catch (e: Exception) {
            "(could not load quests)"
        }
    }

    private fun appendMessage(message: ChatMessage) {
        _uiState.update { it.copy(messages = it.messages + message) }
    }

    private fun updateToolCallConfirmation(messageId: String, state: ConfirmationState) {
        _uiState.update { uiState ->
            uiState.copy(messages = uiState.messages.map { msg ->
                if (msg is ChatMessage.ToolCallMessage && msg.id == messageId) {
                    msg.copy(confirmationState = state)
                } else msg
            })
        }
    }

    private fun reloadService() {
        val intent = Intent(getApplication(), ReminderService::class.java).apply {
            action = ReminderService.ACTION_RELOAD
        }
        getApplication<Application>().startService(intent)
    }

    private fun logException(context: String, e: Throwable) {
        try {
            val eventLog = EventLogStore(getApplication())
            eventLog.add(
                Event.Crash(
                    exceptionClass = e::class.java.name,
                    exceptionMessage = "[$context] ${e.message ?: "(no message)"}",
                    stackTrace = e.stackTraceToString().take(3000)
                )
            )
        } catch (_: Exception) {}
        Log.e("AiViewModel", "Exception in $context", e)
    }

    override fun onCleared() {
        super.onCleared()
        modelManager.unloadEngine()
    }
}
