package se.joynes.nudgealarm.core.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationConditionTest {
    @Test
    fun sameLocationIsInsideRadius() {
        assertTrue(
            LocationCondition.isWithinRadius(
                latitude = 59.3293,
                longitude = 18.0686,
                targetLatitude = 59.3293,
                targetLongitude = 18.0686,
                radiusMeters = 100
            )
        )
    }

    @Test
    fun nearbyLocationIsInsideRadius() {
        assertTrue(
            LocationCondition.isWithinRadius(
                latitude = 59.3293,
                longitude = 18.0686,
                targetLatitude = 59.3300,
                targetLongitude = 18.0686,
                radiusMeters = 100
            )
        )
    }

    @Test
    fun distantLocationIsOutsideRadius() {
        assertFalse(
            LocationCondition.isWithinRadius(
                latitude = 59.3293,
                longitude = 18.0686,
                targetLatitude = 59.3400,
                targetLongitude = 18.0686,
                radiusMeters = 500
            )
        )
    }
}
