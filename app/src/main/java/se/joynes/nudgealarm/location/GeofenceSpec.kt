package se.joynes.nudgealarm.location

import se.joynes.nudgealarm.database.SavedPlaceLocationEntity

internal data class GeofenceSpec(
    val placeId: String,
    val positionId: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int
) {
    val signature: String get() = "$placeId|$positionId|$latitude|$longitude|$radiusMeters"
    companion object {
        fun from(position: SavedPlaceLocationEntity) = GeofenceSpec(
            position.placeId, position.id, position.latitude, position.longitude, position.radiusMeters
        )
    }
}
