package se.joynes.nudgealarm.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.database.*
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme

class ReminderPlacePickerTest {
    @get:Rule val compose = createComposeRule()
    private val home = SavedPlaceWithLocations(SavedPlaceEntity("home", "Home", "position"),
        listOf(SavedPlaceLocationEntity("position", "home", "Home", 59.0, 18.0, 250)))

    @Test fun oneTapSelectsPlaceWithoutAnotherConfirmation() {
        val selected = mutableListOf<String>()
        compose.setContent { NudgeAlarmTheme {
            ReminderPlacePicker(ReminderPlacePickerState("laundry", "Laundry", listOf(home), loading = false),
                { selected.add(it) }, {}, {})
        } }
        compose.onNodeWithTag("quick-place-home").performClick()
        assertEquals(listOf("home"), selected)
    }

    @Test fun noPlacesOffersSetupAndCancelDoesNotAssign() {
        var edited = false
        var dismissed = false
        compose.setContent { NudgeAlarmTheme {
            ReminderPlacePicker(ReminderPlacePickerState("laundry", loading = false),
                { error("Must not assign") }, { edited = true }, { dismissed = true })
        } }
        compose.onNodeWithText("No saved places yet. Add a place in Edit Quest.").assertIsDisplayed()
        compose.onNodeWithText("Edit Quest / Add place").performClick()
        assertEquals(true, edited)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(true, dismissed)
    }

    @Test fun placeWithoutPositionCannotBeSelected() {
        compose.setContent { NudgeAlarmTheme {
            ReminderPlacePicker(ReminderPlacePickerState("laundry", places = listOf(home.copy(locations = emptyList())), loading = false),
                { error("Must not assign") }, {}, {})
        } }
        compose.onNodeWithTag("quick-place-home").assertIsNotEnabled()
    }
}
