package se.joynes.nudgealarm.core.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object LocationCondition {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun distanceMeters(
        latitude: Double,
        longitude: Double,
        targetLatitude: Double,
        targetLongitude: Double
    ): Double {
        val latitudeDelta = Math.toRadians(targetLatitude - latitude)
        val longitudeDelta = Math.toRadians(targetLongitude - longitude)
        val startLatitude = Math.toRadians(latitude)
        val endLatitude = Math.toRadians(targetLatitude)

        val haversine = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(startLatitude) * cos(endLatitude) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(haversine))
    }

    fun isWithinRadius(
        latitude: Double,
        longitude: Double,
        targetLatitude: Double,
        targetLongitude: Double,
        radiusMeters: Int
    ): Boolean = distanceMeters(latitude, longitude, targetLatitude, targetLongitude) <= radiusMeters
}
