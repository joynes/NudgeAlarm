package se.joynes.nudgealarm.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.database.SavedPlaceEntity
import se.joynes.nudgealarm.database.SavedPlaceLocationEntity
import se.joynes.nudgealarm.database.SavedPlaceWithLocations
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme

class SavedPlacesSettingsUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun deleteConfirmationShowsLinkedQuestImpact() {
        var deletedPlace: String? = null
        val home = SavedPlaceWithLocations(
            SavedPlaceEntity(id = "home", name = "Home", activeLocationId = "here"),
            listOf(SavedPlaceLocationEntity("here", "home", "Stockholm", 59.3, 18.0, 250))
        )
        composeRule.setContent {
            NudgeAlarmTheme {
                SavedPlacesSettingsSection(
                    places = listOf(home),
                    linkedReminderCounts = mapOf("home" to 2),
                    error = null,
                    onRenamePlace = { _, _ -> },
                    onUpdatePosition = { _, _, _, _, _, _ -> },
                    onSelectPosition = { _, _ -> },
                    onDeletePosition = { _, _ -> },
                    onDeletePlace = { deletedPlace = it }
                )
            }
        }

        composeRule.onNodeWithText("2 linked quests · 1 saved position").assertIsDisplayed()
        composeRule.onNodeWithTag("delete-place-home").performClick()
        composeRule.onNodeWithText("This removes all 1 saved positions. 2 linked quests will change to ANYWHERE.").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-delete-place").performClick()
        composeRule.runOnIdle { assertEquals("home", deletedPlace) }
    }
}
