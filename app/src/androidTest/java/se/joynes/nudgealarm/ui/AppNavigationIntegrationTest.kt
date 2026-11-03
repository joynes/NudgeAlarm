package se.joynes.nudgealarm.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.MainActivity
import se.joynes.nudgealarm.storage.AppStateStore

class AppNavigationIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        try { node.performScrollTo() } catch (_: AssertionError) { }
        node.performClick()
    }

    @Test fun tabsNestedSettingsPermissionsAndLogCanReturnToTheMainScreen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppStateStore(context).clear()
        ActivityScenario.launch(MainActivity::class.java).use {
            click("EDIT")
            compose.onNodeWithText("QUEST EDITOR").assertIsDisplayed()
            click("+")
            compose.onNodeWithText(">> NEW QUEST").assertIsDisplayed()
            click("< CANCEL")
            click("SCORES")
            compose.onNodeWithText("HIGH SCORES").assertIsDisplayed()
            click("OPTIONS")
            compose.onNodeWithTag("open-snooze-settings").performScrollTo().performClick()
            compose.onNodeWithText("SNOOZE TIMES").assertIsDisplayed()
            click("BACK TO OPTIONS")
            compose.onNodeWithTag("open-settings-transfer").performScrollTo().performClick()
            compose.onNodeWithTag("export-settings").assertIsDisplayed()
            click("BACK TO OPTIONS")
            click(">> PERMISSIONS")
            compose.onNodeWithText("SYSTEM CHECK").assertIsDisplayed()
            click("< BACK TO GAME")
            compose.onNodeWithContentDescription("Open menu").performClick()
            compose.onNodeWithText("> EVENT LOG", substring = true).performClick()
            compose.onNodeWithText("DEBUG LOG").assertIsDisplayed()
            click("< BACK TO GAME")
            compose.onNodeWithContentDescription("Open menu").performClick()
            click("> EXPORT DATA")
            compose.onNodeWithText("Export data and diagnostics").assertIsDisplayed()
            click("CANCEL")
            compose.onNodeWithText("NUDGEALARM").assertIsDisplayed()
        }
    }
}
