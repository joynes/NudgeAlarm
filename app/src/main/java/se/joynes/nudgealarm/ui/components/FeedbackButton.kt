package se.joynes.nudgealarm.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import se.joynes.nudgealarm.audio.SoundManager
import se.joynes.nudgealarm.ui.theme.MegadriveGold

/**
 * Sound effect types for buttons
 */
enum class ButtonSound {
    CLICK,      // Standard button press
    SUCCESS,    // Done/Complete actions
    BACK,       // Back navigation
    START,      // Start game/service
    CANCEL,     // Cancel/Delete actions
    MENU,       // Menu item selection
    NONE        // No sound
}

/**
 * A button with clear visual feedback when pressed.
 * - Inverts colors on press (white background, colored text)
 * - Shows a brief "flash" after click to confirm action
 * - Plays sound effect on click
 */
@Composable
fun FeedbackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MegadriveGold,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    sound: ButtonSound = ButtonSound.CLICK,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Track "just clicked" state for flash effect
    var justClicked by remember { mutableStateOf(false) }

    LaunchedEffect(justClicked) {
        if (justClicked) {
            delay(150) // Brief flash duration
            justClicked = false
        }
    }

    // Play sound when button becomes pressed
    LaunchedEffect(isPressed) {
        if (isPressed && sound != ButtonSound.NONE) {
            when (sound) {
                ButtonSound.CLICK -> SoundManager.playClick()
                ButtonSound.SUCCESS -> SoundManager.playSuccess()
                ButtonSound.BACK -> SoundManager.playBack()
                ButtonSound.START -> SoundManager.playStart()
                ButtonSound.CANCEL -> SoundManager.playCancel()
                ButtonSound.MENU -> SoundManager.playMenuSelect()
                ButtonSound.NONE -> {}
            }
        }
    }

    // Animate colors for smoother transition
    val containerColor by animateColorAsState(
        targetValue = when {
            isLoading -> color.copy(alpha = 0.7f)
            isPressed || justClicked -> Color.White
            else -> color
        },
        animationSpec = tween(durationMillis = 100),
        label = "containerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isLoading -> Color.White.copy(alpha = 0.7f)
            isPressed || justClicked -> color
            else -> Color.White
        },
        animationSpec = tween(durationMillis = 100),
        label = "contentColor"
    )

    Button(
        onClick = {
            if (!isLoading) {
                justClicked = true
                onClick()
            }
        },
        modifier = modifier,
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = color.copy(alpha = 0.5f),
            disabledContentColor = Color.White.copy(alpha = 0.5f)
        ),
        interactionSource = interactionSource,
        contentPadding = contentPadding
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            content()
        }
    }
}

/**
 * An outlined button with clear visual feedback when pressed.
 * - Fills with color on press
 * - Shows a brief "flash" after click to confirm action
 * - Plays sound effect on click
 */
@Composable
fun FeedbackOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MegadriveGold,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    sound: ButtonSound = ButtonSound.CLICK,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Track "just clicked" state for flash effect
    var justClicked by remember { mutableStateOf(false) }

    LaunchedEffect(justClicked) {
        if (justClicked) {
            delay(150) // Brief flash duration
            justClicked = false
        }
    }

    // Play sound when button becomes pressed
    LaunchedEffect(isPressed) {
        if (isPressed && sound != ButtonSound.NONE) {
            when (sound) {
                ButtonSound.CLICK -> SoundManager.playClick()
                ButtonSound.SUCCESS -> SoundManager.playSuccess()
                ButtonSound.BACK -> SoundManager.playBack()
                ButtonSound.START -> SoundManager.playStart()
                ButtonSound.CANCEL -> SoundManager.playCancel()
                ButtonSound.MENU -> SoundManager.playMenuSelect()
                ButtonSound.NONE -> {}
            }
        }
    }

    // Animate colors for smoother transition
    val containerColor by animateColorAsState(
        targetValue = when {
            isLoading -> color.copy(alpha = 0.3f)
            isPressed || justClicked -> color
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 100),
        label = "containerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isLoading -> color.copy(alpha = 0.7f)
            isPressed || justClicked -> Color.White
            else -> color
        },
        animationSpec = tween(durationMillis = 100),
        label = "contentColor"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isPressed || justClicked -> Color.White
            else -> color
        },
        animationSpec = tween(durationMillis = 100),
        label = "borderColor"
    )

    OutlinedButton(
        onClick = {
            if (!isLoading) {
                justClicked = true
                onClick()
            }
        },
        modifier = modifier,
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = color.copy(alpha = 0.5f)
        ),
        interactionSource = interactionSource,
        contentPadding = contentPadding
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = color,
                strokeWidth = 2.dp
            )
        } else {
            content()
        }
    }
}

/**
 * Simple text wrapper for FeedbackButton
 */
@Composable
fun FeedbackButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MegadriveGold,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    sound: ButtonSound = ButtonSound.CLICK
) {
    FeedbackButton(
        onClick = onClick,
        modifier = modifier,
        color = color,
        enabled = enabled,
        isLoading = isLoading,
        sound = sound
    ) {
        Text(text)
    }
}

/**
 * Simple text wrapper for FeedbackOutlinedButton
 */
@Composable
fun FeedbackOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MegadriveGold,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    sound: ButtonSound = ButtonSound.CLICK
) {
    FeedbackOutlinedButton(
        onClick = onClick,
        modifier = modifier,
        color = color,
        enabled = enabled,
        isLoading = isLoading,
        sound = sound
    ) {
        Text(text)
    }
}
