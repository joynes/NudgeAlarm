package se.joynes.nudgealarm.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.database.SavedPlaceEntity
import se.joynes.nudgealarm.database.SavedPlaceLocationEntity
import se.joynes.nudgealarm.database.SavedPlaceWithLocations
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme

class PlaceReminderUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun newQuestShowsAnywhereAndPlaceCreation() {
        showEditor()

        composeRule.onNodeWithText("PLACE").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("place-anywhere").assertIsDisplayed()
        composeRule.onNodeWithText("New saved place, e.g. Home")
            .performScrollTo()
            .performTextInput("Home")
        composeRule.onNodeWithText("SAVE CURRENT POSITION AS NEW PLACE")
            .assertIsEnabled()
    }

    @Test
    fun savedPlaceKeepsOldPositionsAndCanSwitchActivePosition() {
        var selectedPlace: String? = null
        var selectedLocation: String? = null
        showEditor(
            places = listOf(homePlace()),
            onSelectPosition = { placeId, locationId ->
                selectedPlace = placeId
                selectedLocation = locationId
            }
        )

        composeRule.onNodeWithTag("place-home").performScrollTo().performClick()
        composeRule.onAllNodesWithText("ACTIVE").assertCountEquals(1)
        composeRule.onNodeWithTag("position-spain").performScrollTo().performClick()

        assertEquals("home", selectedPlace)
        assertEquals("spain", selectedLocation)
    }

    @Test
    fun questSaveLinksSelectedSavedPlace() {
        var savedPlaceId: String? = null
        showEditor(
            places = listOf(homePlace()),
            onSave = { placeId -> savedPlaceId = placeId }
        )

        composeRule.onNodeWithText("Quest Name").performTextInput("Wash dishes")
        composeRule.onNodeWithTag("place-home").performScrollTo().performClick()
        composeRule.onNodeWithTag("quest-save").performClick()

        assertEquals("home", savedPlaceId)
    }

    @Test
    fun questDefaultsToAnywhere() {
        var savedPlaceId: String? = "not-called"
        showEditor(onSave = { placeId -> savedPlaceId = placeId })

        composeRule.onNodeWithText("Quest Name").performTextInput("Drink water")
        composeRule.onNodeWithTag("quest-save").performClick()

        assertNull(savedPlaceId)
    }

    private fun showEditor(
        places: List<SavedPlaceWithLocations> = emptyList(),
        onSave: (String?) -> Unit = {},
        onSelectPosition: (String, String) -> Unit = { _, _ -> }
    ) {
        composeRule.setContent {
            NudgeAlarmTheme {
                EditRemindersScreen(
                    uiState = EditRemindersUiState(
                        isLoading = false,
                        isAddingNew = true,
                        savedPlaces = places
                    ),
                    onAddNew = {},
                    onEdit = {},
                    onDelete = {},
                    onToggleEnabled = {},
                    onSave = { _, _, _, _, _, placeId -> onSave(placeId) },
                    onCreateSavedPlace = { _, _, _, _ -> },
                    onAddSavedPosition = { _, _, _, _, _ -> },
                    onSelectSavedPosition = onSelectPosition,
                    onCancelEdit = {},
                    onBack = {}
                )
            }
        }
    }

    private fun homePlace(): SavedPlaceWithLocations {
        val stockholm = SavedPlaceLocationEntity(
            id = "stockholm",
            placeId = "home",
            label = "Stockholm",
            latitude = 59.3293,
            longitude = 18.0686,
            radiusMeters = 250
        )
        val spain = SavedPlaceLocationEntity(
            id = "spain",
            placeId = "home",
            label = "Spain",
            latitude = 36.7213,
            longitude = -4.4214,
            radiusMeters = 500
        )
        return SavedPlaceWithLocations(
            place = SavedPlaceEntity(
                id = "home",
                name = "Home",
                activeLocationId = stockholm.id
            ),
            locations = listOf(stockholm, spain)
        )
    }
}
