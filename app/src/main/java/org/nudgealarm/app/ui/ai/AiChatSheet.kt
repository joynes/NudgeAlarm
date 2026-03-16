package org.nudgealarm.app.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nudgealarm.app.ai.AiUiState
import org.nudgealarm.app.ai.model.ModelInfo

private val SheetBg = Color(0xFF0D0D1A)
private val HeaderBg = Color(0xFF111127)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatSheet(
    uiState: AiUiState,
    installedModels: List<ModelInfo>,
    onHide: () -> Unit,
    onSend: () -> Unit,
    onInputChange: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onManageModels: () -> Unit,
    onHideModelDownload: () -> Unit,
    onStartDownload: (ModelInfo) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteModel: (ModelInfo) -> Unit,
    onHfTokenChange: (String) -> Unit,
    onConfirmToolCall: (String) -> Unit,
    onDenyToolCall: (String) -> Unit,
    onClearConversation: () -> Unit,
    onFetchRemoteModels: () -> Unit,
    onSearchQueryChange: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onHide,
        sheetState = sheetState,
        containerColor = SheetBg,
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        if (uiState.showModelDownloadScreen) {
            ModelDownloadScreen(
                downloadStates = uiState.downloadStates,
                installedModels = installedModels,
                hfToken = uiState.hfToken,
                onHfTokenChange = onHfTokenChange,
                onDownload = onStartDownload,
                onCancel = onCancelDownload,
                onDelete = onDeleteModel,
                onClose = onHideModelDownload,
                remoteModels = uiState.remoteModels,
                isLoadingRemoteModels = uiState.isLoadingRemoteModels,
                remoteModelsError = uiState.remoteModelsError,
                modelSearchQuery = uiState.modelSearchQuery,
                onFetchRemoteModels = onFetchRemoteModels,
                onSearchQueryChange = onSearchQueryChange
            )
        } else {
            ChatContent(
                uiState = uiState,
                installedModels = installedModels,
                listState = listState,
                onHide = onHide,
                onSend = onSend,
                onInputChange = onInputChange,
                onSelectModel = onSelectModel,
                onManageModels = onManageModels,
                onConfirmToolCall = onConfirmToolCall,
                onDenyToolCall = onDenyToolCall,
                onClearConversation = onClearConversation
            )
        }
    }
}

@Composable
private fun ChatContent(
    uiState: AiUiState,
    installedModels: List<ModelInfo>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onHide: () -> Unit,
    onSend: () -> Unit,
    onInputChange: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onManageModels: () -> Unit,
    onConfirmToolCall: (String) -> Unit,
    onDenyToolCall: (String) -> Unit,
    onClearConversation: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(HeaderBg)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI Assistant",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                ModelSelectorDropdown(
                    selectedModelId = uiState.selectedModelId,
                    installedModels = installedModels,
                    onSelectModel = onSelectModel,
                    onManageModels = onManageModels
                )
                if (uiState.messages.isNotEmpty()) {
                    TextButton(onClick = onClearConversation) {
                        Text("Clear", color = Color(0xFF90A4AE), fontSize = 11.sp)
                    }
                }
            }
        }

        // Error banner
        if (uiState.error != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF4E1010))
                    .padding(8.dp)
            ) {
                Text(text = uiState.error, color = Color(0xFFEF9A9A), fontSize = 12.sp)
            }
        }

        // Empty state: no models installed
        if (uiState.messages.isEmpty() && installedModels.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("No AI model installed", color = Color(0xFF90A4AE), fontSize = 14.sp)
                    TextButton(onClick = onManageModels) {
                        Text("Download a model", color = Color(0xFF90CAF9))
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    ChatMessageItem(
                        message = message,
                        onConfirmToolCall = onConfirmToolCall,
                        onDenyToolCall = onDenyToolCall
                    )
                }
            }
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(HeaderBg)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.inputText,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        text = if (installedModels.isEmpty()) "Download a model to chat..." else "Ask about your quests...",
                        color = Color(0xFF546E7A),
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = !uiState.isGenerating && installedModels.isNotEmpty(),
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF1976D2),
                    unfocusedBorderColor = Color(0xFF37474F),
                    cursorColor = Color(0xFF90CAF9)
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onSend,
                enabled = uiState.inputText.isNotBlank() && !uiState.isGenerating && installedModels.isNotEmpty()
            ) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = "Send",
                    tint = if (uiState.inputText.isNotBlank() && !uiState.isGenerating) Color(0xFF1976D2) else Color(0xFF37474F),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
