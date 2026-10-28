package se.joynes.nudgealarm.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

class InteractionGraceSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun defaultCanBeChangedAndDisabledWithOneTap() {
        var selected = 15
        compose.setContent { NudgeAlarmTheme {
            var minutes by remember { mutableIntStateOf(15) }
            InteractionGraceSettings(minutes, onChange = { minutes = it; selected = it })
        } }
        compose.onNodeWithTag("interaction-grace-15").assertIsSelected()
        compose.onNodeWithTag("interaction-grace-30").performClick().assertIsSelected()
        assertEquals(30, selected)
        compose.onNodeWithTag("interaction-grace-0").performClick().assertIsSelected()
        compose.onNodeWithText("No automatic break").assertIsDisplayed()
        assertEquals(0, selected)
    }

    @Test fun customDurationUsesCurrentValueAndCancelKeepsIt() {
        var selected = 47
        compose.setContent { NudgeAlarmTheme { InteractionGraceSettings(selected, onChange = { selected = it }) } }
        compose.onNodeWithText("Custom…").performClick()
        compose.onNodeWithTag("grace-hours").assertExists()
        compose.onNodeWithTag("grace-minutes").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(47, selected)
        compose.onNodeWithText("Custom…").performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(47, selected)
    }

    @Test fun unlockIsDefaultAndAppOnlyCanBeSelected() {
        var selected = "unlock"
        compose.setContent { NudgeAlarmTheme {
            var source by remember { mutableStateOf("unlock") }
            InteractionGraceSettings(15, {}, source) { source = it; selected = it }
        } }
        compose.onNodeWithTag("grace-source-unlock").assertIsSelected()
        InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let { directory ->
            val output = File(directory, "phone-use-settings.png")
            output.parentFile?.mkdirs()
            output.outputStream().use {
                compose.onNodeWithTag("interaction-grace-settings").captureToImage().asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithTag("grace-source-app").performClick().assertIsSelected()
        assertEquals("app", selected)
    }

    @Test fun detectingOtherAppsRequiresDisclosureAndCancelDoesNotEnableIt() {
        var selected = "unlock"
        compose.setContent { NudgeAlarmTheme { InteractionGraceSettings(15, {}, selected) { selected = it } } }
        compose.onNodeWithTag("grace-source-usage").performClick()
        compose.onNodeWithText("Detect use in other apps").assertIsDisplayed()
        compose.onNodeWithTag("open-usage-access").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals("unlock", selected)
    }
}
