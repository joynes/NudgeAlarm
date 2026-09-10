package se.joynes.nudgealarm.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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

    @Query("UPDATE saved_places SET activeLocationId = :locationId, updatedAt = :updatedAt WHERE id = :placeId")
    suspend fun setActiveLocation(placeId: String, locationId: String, updatedAt: Long)

    @Query("DELETE FROM saved_places")
    suspend fun deleteAllPlaces()

    @Query("DELETE FROM saved_place_locations")
    suspend fun deleteAllLocations()
}
