package se.joynes.nudgealarm.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface SavedPlaceDao {
    @Query("SELECT * FROM saved_places ORDER BY name COLLATE NOCASE")
    suspend fun getAllPlaces(): List<SavedPlaceEntity>

    @Query("SELECT * FROM saved_places WHERE id = :id")
    suspend fun getPlace(id: String): SavedPlaceEntity?

    @Query("SELECT * FROM saved_place_locations WHERE placeId = :placeId ORDER BY createdAt DESC")
    suspend fun getLocations(placeId: String): List<SavedPlaceLocationEntity>

    @Query("SELECT * FROM saved_place_locations WHERE id = :id")
    suspend fun getLocation(id: String): SavedPlaceLocationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlace(place: SavedPlaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLocation(location: SavedPlaceLocationEntity)

    @Update
    suspend fun updatePlace(place: SavedPlaceEntity)

    @Update
    suspend fun updateLocation(location: SavedPlaceLocationEntity)

    @Query("UPDATE saved_places SET activeLocationId = :locationId, updatedAt = :updatedAt WHERE id = :placeId")
    suspend fun setActiveLocation(placeId: String, locationId: String, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM reminders WHERE placeId = :placeId")
    suspend fun getLinkedReminderCount(placeId: String): Int

    @Query("UPDATE reminders SET placeId = NULL, updatedAt = :updatedAt WHERE placeId = :placeId")
    suspend fun unlinkReminders(placeId: String, updatedAt: Long)

    @Query("DELETE FROM saved_place_locations WHERE id = :locationId")
    suspend fun deleteLocationById(locationId: String)

    @Query("DELETE FROM saved_place_locations WHERE placeId = :placeId")
    suspend fun deleteLocationsForPlace(placeId: String)

    @Query("DELETE FROM saved_places WHERE id = :placeId")
    suspend fun deletePlaceById(placeId: String)

    @Transaction
    suspend fun deletePlaceAndUnlink(placeId: String) {
        require(getPlace(placeId) != null) { "Saved place not found" }
        unlinkReminders(placeId, System.currentTimeMillis())
        deleteLocationsForPlace(placeId)
        deletePlaceById(placeId)
    }

    @Transaction
    suspend fun deletePosition(placeId: String, locationId: String) {
        val place = requireNotNull(getPlace(placeId)) { "Saved place not found" }
        require(getLocation(locationId)?.placeId == placeId) { "Position does not belong to place" }
        val remaining = getLocations(placeId).filterNot { it.id == locationId }
        require(remaining.isNotEmpty()) { "Delete the saved place instead of its last position" }
        if (place.activeLocationId == locationId) {
            setActiveLocation(placeId, remaining.first().id, System.currentTimeMillis())
        }
        deleteLocationById(locationId)
    }

    @Query("DELETE FROM saved_places")
    suspend fun deleteAllPlaces()

    @Query("DELETE FROM saved_place_locations")
    suspend fun deleteAllLocations()
}
