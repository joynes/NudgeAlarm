package se.joynes.nudgealarm.database

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedPlaceWithLocationsTest {
    @Test
    fun activeLocationCanChangeWithoutChangingPlaceId() {
        val first = SavedPlaceLocationEntity(
            id = "old-home",
            placeId = "home",
            label = "Position 1",
            latitude = 59.0,
            longitude = 18.0,
            radiusMeters = 250
        )
        val second = first.copy(id = "travel-home", label = "Position 2", latitude = 40.0)

        val original = SavedPlaceWithLocations(
            place = SavedPlaceEntity(id = "home", name = "Home", activeLocationId = first.id),
            locations = listOf(first, second)
        )
        val switched = original.copy(place = original.place.copy(activeLocationId = second.id))

        assertEquals("home", switched.place.id)
        assertEquals("travel-home", switched.activeLocation?.id)
        assertEquals(2, switched.locations.size)
    }
}
