package se.joynes.nudgealarm.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsNotEnabled
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.database.SavedPlaceEntity
import se.joynes.nudgealarm.database.SavedPlaceLocationEntity
import se.joynes.nudgealarm.database.SavedPlaceWithLocations
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme

class SavedPlacesSettingsUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun placeAndPositionEditorsValidateInputAndPreserveCancelledEdits() {
        val home = SavedPlaceWithLocations(SavedPlaceEntity("home", "Home", "here"), listOf(
            SavedPlaceLocationEntity("here", "home", "Stockholm", 59.3, 18.0, 250),
            SavedPlaceLocationEntity("away", "home", "Spain", 40.0, -3.0, 300)))
        val events = mutableListOf<List<Any>>()
        composeRule.setContent { NudgeAlarmTheme {
            SavedPlacesSettingsSection(listOf(home), mapOf("home" to 1), "Example validation error",
                { id, name -> events.add(listOf("rename", id, name)) },
                { p, id, label, lat, lon, radius -> events.add(listOf("update", p, id, label, lat, lon, radius)) },
                { p, id -> events.add(listOf("select", p, id)) }, { _, _ -> }, {},
                { p, label, lat, lon, radius -> events.add(listOf("add", p, label, lat, lon, radius)) })
        } }
        composeRule.onNodeWithText("Example validation error").assertIsDisplayed()
        composeRule.onNodeWithTag("open-place-home").performClick()
        composeRule.onNodeWithTag("rename-place-home").performClick()
        composeRule.onNodeWithText("Place name").performTextReplacement("")
        composeRule.onNodeWithText("SAVE").assertIsNotEnabled()
        composeRule.onNodeWithText("Place name").performTextReplacement("At home")
        composeRule.onNodeWithText("SAVE").performClick()
        composeRule.onNodeWithTag("use-position-away").performClick()
        composeRule.onNodeWithTag("edit-position-away").performClick()
        composeRule.onNodeWithText("Latitude").performTextReplacement("91")
        composeRule.onNodeWithText("SAVE").assertIsNotEnabled()
        composeRule.onNodeWithText("Latitude").performTextReplacement("41.5")
        composeRule.onNodeWithText("Longitude").performTextReplacement("-4.5")
        composeRule.onNodeWithText("Radius in meters").performTextReplacement("400")
        composeRule.onNodeWithText("Position label").performTextReplacement("Holiday")
        composeRule.onNodeWithText("SAVE").performClick()
        composeRule.onNodeWithText("+ ADD POSITION").performClick()
        composeRule.onNodeWithText("SAVE").assertIsNotEnabled()
        composeRule.onNodeWithText("Position label").performTextReplacement("Office")
        composeRule.onNodeWithText("Latitude").performTextReplacement("59.0")
        composeRule.onNodeWithText("Longitude").performTextReplacement("18.5")
        composeRule.onNodeWithText("SAVE").performClick()
        composeRule.onNodeWithTag("rename-place-home").performClick()
        composeRule.onNodeWithText("CANCEL").performClick()
        composeRule.onNodeWithTag("edit-position-away").performClick()
        composeRule.onNodeWithText("CANCEL").performClick()
        composeRule.onNodeWithTag("delete-position-away").performClick()
        composeRule.onNodeWithText("Spain will be removed. If it is active, another saved position becomes active.").assertIsDisplayed()
        composeRule.onNodeWithText("CANCEL").performClick()
        composeRule.onNodeWithTag("delete-place-home").performClick()
        composeRule.onNodeWithText("CANCEL").performClick()
        composeRule.runOnIdle { assertEquals(listOf(
            listOf("rename", "home", "At home"), listOf("select", "home", "away"),
            listOf("update", "home", "away", "Holiday", 41.5, -4.5, 400),
            listOf("add", "home", "Office", 59.0, 18.5, 200)), events) }
    }

    @Test
    fun lastPositionCanBeDeletedOnlyAfterConfirmationInDetailMenu() {
        var deleted: String? = null
        val home = SavedPlaceWithLocations(
            SavedPlaceEntity("home", "Home", "here"),
            listOf(SavedPlaceLocationEntity("here", "home", "Stockholm", 59.3, 18.0, 250))
        )
        composeRule.setContent {
            NudgeAlarmTheme {
                SavedPlacesSettingsSection(listOf(home), mapOf("home" to 2), null,
                    { _, _ -> }, { _, _, _, _, _, _ -> }, { _, _ -> },
                    { _, id -> deleted = id }, {})
            }
        }
        composeRule.onNodeWithTag("delete-position-here").assertDoesNotExist()
        composeRule.onNodeWithTag("open-place-home").performClick()
        composeRule.onNodeWithTag("delete-position-here").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(null, deleted) }
        composeRule.onNodeWithText("Stockholm will be removed. The place and quest links are kept. Alerts stay paused until you add a new position.").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-delete-position").performClick()
        composeRule.runOnIdle { assertEquals("here", deleted) }
        composeRule.onNodeWithText("‹ ALL PLACES").performClick()
        composeRule.onNodeWithTag("open-place-home").assertIsDisplayed()
    }

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

        composeRule.onNodeWithTag("open-place-home").performClick()
        composeRule.onNodeWithText("2 linked quests · 1 saved position").assertIsDisplayed()
        composeRule.onNodeWithTag("delete-place-home").performClick()
        composeRule.onNodeWithText("This removes all 1 saved positions. 2 linked quests will change to ANYWHERE.").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-delete-place").performClick()
        composeRule.runOnIdle { assertEquals("home", deletedPlace) }
    }
}
