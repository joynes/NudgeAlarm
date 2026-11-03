package se.joynes.nudgealarm.ui

import android.Manifest
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.database.*
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme

class QuestLocationCaptureTest {
    @get:Rule val compose = createComposeRule()
    private fun click(text: String) { compose.onNodeWithText(text).performScrollTo().performClick() }

    @Suppress("DEPRECATION")
    @Test fun savingHereCreatesAReusablePlaceAndAnotherPositionKeepsTheSameQuestLink() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(LocationManager::class.java)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        automation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        automation.executeShellCommand("appops set ${context.packageName} android:mock_location allow")
            .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        manager.addTestProvider(LocationManager.GPS_PROVIDER, false, false, false, false, true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE)
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
        val handler = Handler(Looper.getMainLooper())
        val update = object : Runnable {
            override fun run() {
                manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, Location(LocationManager.GPS_PROVIDER).apply {
                    latitude = 59.3; longitude = 18.0; accuracy = 3f
                    time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                })
                handler.postDelayed(this, 200)
            }
        }
        handler.post(update)
        var places by mutableStateOf(emptyList<SavedPlaceWithLocations>())
        val actions = mutableListOf<String>()
        try {
            compose.setContent { NudgeAlarmTheme {
                EditRemindersScreen(EditRemindersUiState(isLoading = false, isAddingNew = true, savedPlaces = places),
                    onAddNew = {}, onEdit = {}, onDelete = {}, onToggleEnabled = {},
                    onSave = { _, _, _, _, _, place -> actions.add("quest:$place") },
                    onCreateSavedPlace = { name, lat, lon, radius ->
                        assertEquals(59.3, lat, 0.0001); assertEquals(18.0, lon, 0.0001)
                        places = listOf(SavedPlaceWithLocations(SavedPlaceEntity("home", name, "first"),
                            listOf(SavedPlaceLocationEntity("first", "home", "Original", lat, lon, radius))))
                        actions.add("create:$name")
                    },
                    onAddSavedPosition = { id, label, lat, lon, radius ->
                        assertEquals("home", id)
                        places = listOf(places.single().let { it.copy(place = it.place.copy(activeLocationId = "second"),
                            locations = it.locations + SavedPlaceLocationEntity("second", id, label, lat, lon, radius)) })
                        actions.add("position:$label")
                    }, onSelectSavedPosition = { _, _ -> }, onCancelEdit = {}, onBack = {})
            } }
            compose.onNodeWithText("Quest Name").performTextInput("Laundry")
            compose.onNodeWithText("New saved place, e.g. Home").performScrollTo().performTextInput("Home")
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(500f) }
            click("SAVE CURRENT POSITION AS NEW PLACE")
            compose.waitUntil(15000) { actions.contains("create:Home") }
            compose.onNodeWithTag("place-home").assertIsSelected()
            compose.onNodeWithText("Position label, e.g. Stockholm or Spain").performScrollTo().performTextInput("Holiday")
            click("ADD HERE AND MAKE ACTIVE")
            compose.waitUntil(15000) { actions.contains("position:Holiday") }
            assertEquals(2, places.single().locations.size)
            assertEquals(500, places.single().locations.first().radiusMeters)
            compose.onNodeWithTag("quest-save").performClick()
            assertEquals(listOf("create:Home", "position:Holiday", "quest:home"), actions)
        } finally {
            handler.removeCallbacks(update)
            manager.removeTestProvider(LocationManager.GPS_PROVIDER)
            automation.executeShellCommand("appops set ${context.packageName} android:mock_location default")
                .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        }
    }
}
