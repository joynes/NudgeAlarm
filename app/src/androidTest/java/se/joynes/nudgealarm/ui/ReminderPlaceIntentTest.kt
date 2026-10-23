package se.joynes.nudgealarm.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.MainActivity
import se.joynes.nudgealarm.database.*
import se.joynes.nudgealarm.notification.ReminderNotification

class ReminderPlaceIntentTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun notificationOpensPickerSurvivesRotationAndSavesThenHandlesAnotherIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = NagDatabase.getInstance(context)
        val ids = listOf("quick_place_test_a", "quick_place_test_b")
        val places = SavedPlaceRepository(db.savedPlaceDao())
        val home = runBlocking {
            ids.forEach { db.reminderDao().insert(ReminderEntity(it, "Test $it", "0 9 * * *")) }
            places.createPlace("Test home", 59.0, 18.0, 250)
        }
        try {
            val intent = Intent(context, MainActivity::class.java)
                .putExtra(ReminderNotification.EXTRA_PICK_PLACE, ids[0])
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                compose.waitUntil(10000) { compose.onAllNodesWithTag("quick-place-${home.id}").fetchSemanticsNodes().isNotEmpty() }
                scenario.recreate()
                compose.onNodeWithText("Only at a place").assertIsDisplayed()
                compose.onNodeWithTag("quick-place-${home.id}").performScrollTo().performClick()
                compose.waitUntil(10000) { runBlocking { db.reminderDao().getById(ids[0])?.placeId == home.id } }
                compose.waitUntil(10000) { compose.onAllNodesWithText("Only at a place").fetchSemanticsNodes().isEmpty() }
                scenario.onActivity { ReminderNotification.createPlaceIntent(it, ids[1]).send() }
                compose.waitUntil(10000) { compose.onAllNodesWithText("Test ${ids[1]}").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Cancel").performClick()
                assertEquals(null, runBlocking { db.reminderDao().getById(ids[1])?.placeId })
            }
        } finally {
            runBlocking { ids.forEach { db.reminderDao().deleteById(it) }; places.deletePlace(home.id) }
        }
    }
}
