package org.nudgealarm.app.ai

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.nudgealarm.app.ai.conversation.ChatMessage
import org.nudgealarm.app.ai.conversation.ConfirmationState
import org.nudgealarm.app.ai.model.DownloadState
import org.nudgealarm.app.ai.model.ModelInfo
import org.nudgealarm.app.ai.tools.ToolCall
import org.nudgealarm.app.ui.ai.AiChatSheet
import org.nudgealarm.app.ui.ai.ToolConfirmationCard

@RunWith(AndroidJUnit4::class)
class AiChatSheetUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val fakeModel = ModelInfo(
        id = "gemma_2b", name = "Gemma 2B", description = "Test model",
        sizeBytes = 1_183_647_366L, huggingFaceUrl = "", filename = "gemma.bin"
    )

    private fun defaultState(
        messages: List<ChatMessage> = emptyList(),
        isGenerating: Boolean = false,
        isSheetVisible: Boolean = true,
        showDownloadScreen: Boolean = false,
        error: String? = null,
        inputText: String = ""
    ) = AiUiState(
        isSheetVisible = isSheetVisible,
        messages = messages,
        inputText = inputText,
        selectedModelId = fakeModel.id,
        downloadStates = emptyMap(),
        isGenerating = isGenerating,
        error = error,
        showModelDownloadScreen = showDownloadScreen,
        hfToken = ""
    )

    private fun setSheet(
        state: AiUiState = defaultState(),
        installedModels: List<ModelInfo> = listOf(fakeModel),
        onSend: () -> Unit = {},
        onInputChange: (String) -> Unit = {},
        onConfirm: (String) -> Unit = {},
        onDeny: (String) -> Unit = {}
    ) {
        composeRule.setContent {
            AiChatSheet(
                uiState = state,
                installedModels = installedModels,
                onHide = {},
                onSend = onSend,
                onInputChange = onInputChange,
                onSelectModel = {},
                onManageModels = {},
                onHideModelDownload = {},
                onStartDownload = {},
                onCancelDownload = {},
                onDeleteModel = {},
                onHfTokenChange = {},
                onConfirmToolCall = onConfirm,
                onDenyToolCall = onDeny,
                onClearConversation = {},
                onFetchRemoteModels = {},
                onSearchQueryChange = {}
            )
        }
    }

    @Test
    fun chatHeaderIsVisible() {
        setSheet()
        composeRule.onNodeWithText("AI Assistant").assertIsDisplayed()
    }

    @Test
    fun emptyStateShowsDownloadPromptWhenNoModels() {
        setSheet(installedModels = emptyList())
        composeRule.onNodeWithText("No AI model installed").assertIsDisplayed()
        composeRule.onNodeWithText("Download a model").assertIsDisplayed()
    }

    @Test
    fun userMessageIsDisplayed() {
        setSheet(state = defaultState(messages = listOf(ChatMessage.User("Hello AI"))))
        composeRule.onNodeWithText("Hello AI").assertIsDisplayed()
    }

    @Test
    fun assistantMessageIsDisplayed() {
        setSheet(state = defaultState(messages = listOf(
            ChatMessage.User("Hi"),
            ChatMessage.Assistant("Hello! How can I help?")
        )))
        composeRule.onNodeWithText("Hello! How can I help?").assertIsDisplayed()
    }

    @Test
    fun streamingAssistantShowsThinkingIndicator() {
        setSheet(state = defaultState(messages = listOf(
            ChatMessage.Assistant("I am thinking", isStreaming = true)
        )))
        composeRule.onNodeWithText("Thinking...").assertIsDisplayed()
    }

    @Test
    fun systemMessageIsDisplayed() {
        setSheet(state = defaultState(messages = listOf(
            ChatMessage.SystemMessage("User denied the tool call.")
        )))
        composeRule.onNodeWithText("User denied the tool call.").assertIsDisplayed()
    }

    @Test
    fun sendButtonCallsOnSend() {
        var sendCalled = false
        setSheet(
            state = defaultState(inputText = "Hello"),
            onSend = { sendCalled = true },
            onInputChange = {}
        )
        composeRule.onNodeWithContentDescription("Send").performClick()
        assertTrue(sendCalled)
    }

    @Test
    fun sendButtonDisabledWhenInputEmpty() {
        setSheet(state = defaultState(inputText = ""))
        composeRule.onNodeWithContentDescription("Send").assertIsNotEnabled()
    }

    @Test
    fun sendButtonDisabledWhenGenerating() {
        setSheet(state = defaultState(inputText = "Hello", isGenerating = true))
        composeRule.onNodeWithContentDescription("Send").assertIsNotEnabled()
    }

    @Test
    fun errorBannerAppearsWhenErrorSet() {
        setSheet(state = defaultState(error = "Failed to load model"))
        composeRule.onNodeWithText("Failed to load model").assertIsDisplayed()
    }

    @Test
    fun clearButtonAppearsWhenMessagesExist() {
        setSheet(state = defaultState(messages = listOf(ChatMessage.User("Hi"))))
        composeRule.onNodeWithText("Clear").assertIsDisplayed()
    }

    @Test
    fun clearButtonNotVisibleWhenNoMessages() {
        setSheet(state = defaultState(messages = emptyList()))
        composeRule.onNodeWithText("Clear").assertDoesNotExist()
    }

    @Test
    fun toolResultMessageDisplaysToolName() {
        setSheet(state = defaultState(messages = listOf(
            org.nudgealarm.app.ai.conversation.ChatMessage.ToolResultMessage(
                toolName = "listQuests",
                result = org.nudgealarm.app.ai.tools.ToolResult.Success("""[{"title":"Run"}]""")
            )
        )))
        composeRule.onNodeWithText("Tool: listQuests").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class ToolConfirmationCardUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleToolCall = ToolCall(
        name = "createQuest",
        arguments = mapOf("title" to "Morning Run", "schedule" to "0 9 * * 1-5")
    )

    @Test
    fun pendingCardShowsToolNameAndButtons() {
        composeRule.setContent {
            ToolConfirmationCard(
                message = ChatMessage.ToolCallMessage(sampleToolCall, ConfirmationState.PENDING),
                onConfirm = {},
                onDeny = {}
            )
        }
        composeRule.onNodeWithText("Tool Call: createQuest").assertIsDisplayed()
        composeRule.onNodeWithText("ALLOW").assertIsDisplayed()
        composeRule.onNodeWithText("DENY").assertIsDisplayed()
    }

    @Test
    fun pendingCardShowsArguments() {
        composeRule.setContent {
            ToolConfirmationCard(
                message = ChatMessage.ToolCallMessage(sampleToolCall, ConfirmationState.PENDING),
                onConfirm = {},
                onDeny = {}
            )
        }
        composeRule.onNodeWithText("  title: Morning Run").assertIsDisplayed()
    }

    @Test
    fun allowButtonCallsOnConfirm() {
        var confirmed = false
        composeRule.setContent {
            ToolConfirmationCard(
                message = ChatMessage.ToolCallMessage(sampleToolCall, ConfirmationState.PENDING),
                onConfirm = { confirmed = true },
                onDeny = {}
            )
        }
        composeRule.onNodeWithText("ALLOW").performClick()
        assertTrue(confirmed)
    }

    @Test
    fun denyButtonCallsOnDeny() {
        var denied = false
        composeRule.setContent {
            ToolConfirmationCard(
                message = ChatMessage.ToolCallMessage(sampleToolCall, ConfirmationState.PENDING),
                onConfirm = {},
                onDeny = { denied = true }
            )
        }
        composeRule.onNodeWithText("DENY").performClick()
        assertTrue(denied)
    }

    @Test
    fun confirmedCardShowsConfirmedStatus() {
        composeRule.setContent {
            ToolConfirmationCard(
                message = ChatMessage.ToolCallMessage(sampleToolCall, ConfirmationState.CONFIRMED),
                onConfirm = {},
                onDeny = {}
            )
        }
        composeRule.onNodeWithText("✓ Allowed").assertIsDisplayed()
        composeRule.onNodeWithText("ALLOW").assertDoesNotExist()
    }

    @Test
    fun deniedCardShowsDeniedStatus() {
        composeRule.setContent {
            ToolConfirmationCard(
                message = ChatMessage.ToolCallMessage(sampleToolCall, ConfirmationState.DENIED),
                onConfirm = {},
                onDeny = {}
            )
        }
        composeRule.onNodeWithText("✗ Denied").assertIsDisplayed()
        composeRule.onNodeWithText("DENY").assertDoesNotExist()
    }
}
