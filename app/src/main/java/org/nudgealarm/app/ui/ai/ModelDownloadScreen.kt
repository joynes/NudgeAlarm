package org.nudgealarm.app.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nudgealarm.app.ai.model.DownloadState
import org.nudgealarm.app.ai.model.ModelCatalog
import org.nudgealarm.app.ai.model.ModelInfo

@Composable
fun ModelDownloadScreen(
    downloadStates: Map<String, DownloadState>,
    installedModels: List<ModelInfo>,
    hfToken: String,
    onHfTokenChange: (String) -> Unit,
    onDownload: (ModelInfo) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (ModelInfo) -> Unit,
    onClose: () -> Unit,
    // Remote browser
    remoteModels: List<ModelInfo> = emptyList(),
    isLoadingRemoteModels: Boolean = false,
    remoteModelsError: String? = null,
    modelSearchQuery: String = "",
    onFetchRemoteModels: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D0D1A))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI Models",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.Gray)
            }
        }

        Text(
            text = "Models run entirely on-device. No data leaves your phone.",
            color = Color(0xFF90A4AE),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        // HuggingFace token section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A2E), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                text = "HuggingFace Access Token",
                color = Color(0xFFFFD54F),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "All models are gated. Accept the license on each model's HF page, then paste your token here:",
                color = Color(0xFF90A4AE),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
            )
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            val linkStyle = androidx.compose.ui.text.SpanStyle(
                color = Color(0xFF90CAF9),
                fontSize = 11.sp,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.ClickableText(
                    text = androidx.compose.ui.text.buildAnnotatedString { pushStyle(linkStyle); append("Gemma 3 1B license"); pop() },
                    onClick = { uriHandler.openUri("https://huggingface.co/litert-community/Gemma3-1B-IT") }
                )
                Text("  •  ", color = Color(0xFF546E7A), fontSize = 11.sp)
                androidx.compose.foundation.text.ClickableText(
                    text = androidx.compose.ui.text.buildAnnotatedString { pushStyle(linkStyle); append("Gemma 2 2B license"); pop() },
                    onClick = { uriHandler.openUri("https://huggingface.co/litert-community/Gemma2-2B-IT") }
                )
                Text("  •  ", color = Color(0xFF546E7A), fontSize = 11.sp)
                androidx.compose.foundation.text.ClickableText(
                    text = androidx.compose.ui.text.buildAnnotatedString { pushStyle(linkStyle); append("Create token"); pop() },
                    onClick = { uriHandler.openUri("https://huggingface.co/settings/tokens") }
                )
            }
            OutlinedTextField(
                value = hfToken,
                onValueChange = onHfTokenChange,
                placeholder = { Text("hf_xxxxxxxxxxxxxxxxxxxx", color = Color(0xFF546E7A), fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFFD54F),
                    unfocusedBorderColor = Color(0xFF37474F)
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModelCatalog.models.forEach { model ->
                val isInstalled = installedModels.any { it.id == model.id }
                val downloadState = downloadStates[model.id] ?: DownloadState.Idle
                ModelCard(
                    model = model,
                    isInstalled = isInstalled,
                    downloadState = downloadState,
                    hasToken = hfToken.isNotBlank(),
                    onDownload = { onDownload(model) },
                    onCancel = { onCancel(model.id) },
                    onDelete = { onDelete(model) }
                )
            }

            // Remote model browser
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A2E), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Browse HuggingFace", color = Color(0xFFFFD54F), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if (isLoadingRemoteModels) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFF90CAF9),
                            strokeWidth = 2.dp
                        )
                    } else {
                        OutlinedButton(
                            onClick = onFetchRemoteModels,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF90CAF9))
                        ) { Text("litert-community", fontSize = 11.sp) }
                    }
                }

                OutlinedTextField(
                    value = modelSearchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search HuggingFace (e.g. gemma mediapipe)", color = Color(0xFF546E7A), fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF90CAF9),
                        unfocusedBorderColor = Color(0xFF37474F)
                    )
                )

                if (remoteModelsError != null) {
                    Text(remoteModelsError, color = Color(0xFFEF9A9A), fontSize = 11.sp)
                }

                remoteModels.forEach { model ->
                    val alreadyInCatalog = org.nudgealarm.app.ai.model.ModelCatalog.models.any { it.id == model.id }
                    val isInstalled = installedModels.any { it.filename == model.filename }
                    val dlState = downloadStates[model.id] ?: DownloadState.Idle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(model.name, color = Color.White, fontSize = 12.sp)
                            Text("${model.sizeMb} MB  •  ${model.description}", color = Color(0xFF78909C), fontSize = 10.sp, maxLines = 1)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        when {
                            isInstalled -> Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF66BB6A), modifier = Modifier.size(20.dp))
                            dlState is DownloadState.Downloading -> androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF90CAF9))
                            else -> Button(
                                onClick = { onDownload(model) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) { Text("Get", fontSize = 11.sp) }
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun ModelCard(
    model: ModelInfo,
    isInstalled: Boolean,
    downloadState: DownloadState,
    hasToken: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A2E), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(model.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${model.sizeMb} MB", color = Color(0xFF90A4AE), fontSize = 11.sp)
            }

            if (isInstalled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Installed", tint = Color(0xFF66BB6A), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFF90A4AE), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Text(model.description, color = Color(0xFFB0BEC5), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))

        when {
            isInstalled -> Text("Installed ✓", color = Color(0xFF66BB6A), fontSize = 12.sp)

            downloadState is DownloadState.Downloading -> {
                Column {
                    Text("Downloading... ${downloadState.progress}%", color = Color(0xFF90CAF9), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { downloadState.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF1976D2),
                        trackColor = Color(0xFF263238)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350))
                    ) { Text("Cancel", fontSize = 12.sp) }
                }
            }

            downloadState is DownloadState.Failed -> {
                val isLicenseError = downloadState.error.contains("License not accepted", ignoreCase = true) ||
                    downloadState.error.contains("403", ignoreCase = true)
                Text(
                    text = downloadState.error,
                    color = Color(0xFFEF9A9A),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                if (isLicenseError && model.licenseUrl != null) {
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    Button(
                        onClick = { uriHandler.openUri(model.licenseUrl) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("⚠ Accept License on HuggingFace →", fontSize = 12.sp) }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                    enabled = !model.requiresHfToken || hasToken
                ) { Text("Retry", fontSize = 12.sp) }
            }

            else -> {
                if (model.requiresHfToken && !hasToken) {
                    Text(
                        text = "⚠ Enter your HuggingFace token above to download",
                        color = Color(0xFFFFD54F),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                    enabled = !model.requiresHfToken || hasToken
                ) { Text("Download (${model.sizeMb} MB)", fontSize = 12.sp) }
            }
        }
    }
}
