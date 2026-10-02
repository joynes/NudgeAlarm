package se.joynes.nudgealarm.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import se.joynes.nudgealarm.database.SavedPlaceLocationEntity

class GeofenceSpecTest {
    @Test fun editsAndPositionSwitchesInvalidateOldPresenceButRenameDoesNot() {
        val position = SavedPlaceLocationEntity("first", "home", "Home", 59.3, 18.0, 250)
        val spec = GeofenceSpec.from(position)
        assertEquals(spec, GeofenceSpec.from(position.copy(label = "Renamed")))
        listOf(position.copy(id = "holiday"), position.copy(latitude = 59.4),
            position.copy(longitude = 18.1), position.copy(radiusMeters = 200),
            position.copy(placeId = "office")).forEach {
            assertNotEquals(spec.signature, GeofenceSpec.from(it).signature)
        }
    }
}
