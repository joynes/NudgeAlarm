package se.joynes.nudgealarm.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import se.joynes.nudgealarm.audio.SoundManager
import se.joynes.nudgealarm.ui.components.*
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme

@RunWith(Parameterized::class)
class FeedbackButtonsTest(private val outlined: Boolean) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "outlined={0}") fun variants() = listOf(arrayOf(false), arrayOf(true))
    }
    @get:Rule val compose = createComposeRule()

    @Test fun physicalPressesDispatchOnceAndDisabledOrLoadingButtonsDoNotDispatch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val events = mutableListOf<ButtonSound>()
        var loading by mutableStateOf(true)
        var enabled by mutableStateOf(false)
        SoundManager.initialize(context)
        SoundManager.initialize(context) // Re-entry must keep the same native pool alive.
        try {
            compose.setContent { NudgeAlarmTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ButtonSound.entries.forEach { sound ->
                        if (outlined) FeedbackOutlinedButton(sound.name, { events.add(sound) },
                            Modifier.testTag(sound.name), enabled = enabled, isLoading = loading, sound = sound)
                        else FeedbackButton(sound.name, { events.add(sound) },
                            Modifier.testTag(sound.name), enabled = enabled, isLoading = loading, sound = sound)
                    }
                }
            } }
            compose.onNodeWithTag("CLICK").assertIsNotEnabled().performClick()
            assertTrue(events.isEmpty())
            compose.runOnIdle { loading = false }
            compose.onNodeWithTag("CLICK").assertIsNotEnabled().performClick()
            assertTrue(events.isEmpty())
            compose.runOnIdle { enabled = true }
            ButtonSound.entries.forEach { sound ->
                val button = compose.onNodeWithTag(sound.name).performScrollTo()
                button.performTouchInput { down(center) }
                compose.waitForIdle()
                button.performTouchInput { up() }
                compose.waitForIdle()
            }
            assertEquals(ButtonSound.entries.toList(), events)
            SoundManager.setSoundEnabled(false)
            assertFalse(SoundManager.isSoundEnabled())
            listOf(SoundManager::playClick, SoundManager::playMenuSelect, SoundManager::playSuccess,
                SoundManager::playBack, SoundManager::playStart, SoundManager::playCancel).forEach { it() }
            assertEquals(ButtonSound.entries.toList(), events)
        } finally {
            SoundManager.setSoundEnabled(true)
            SoundManager.release()
        }
    }
}
