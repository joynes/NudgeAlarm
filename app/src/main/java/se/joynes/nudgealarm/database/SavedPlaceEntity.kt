package se.joynes.nudgealarm.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "saved_places")
data class SavedPlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val activeLocationId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "saved_place_locations",
    indices = [Index("placeId")]
)
data class SavedPlaceLocationEntity(
    @PrimaryKey val id: String,
    val placeId: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val createdAt: Long = System.currentTimeMillis()
)

data class SavedPlaceWithLocations(
    val place: SavedPlaceEntity,
    val locations: List<SavedPlaceLocationEntity>
) {
    val activeLocation: SavedPlaceLocationEntity?
        get() = locations.firstOrNull { it.id == place.activeLocationId }
}
