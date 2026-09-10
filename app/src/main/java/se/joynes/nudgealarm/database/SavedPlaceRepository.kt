package se.joynes.nudgealarm.database

import java.util.UUID

class SavedPlaceRepository(private val dao: SavedPlaceDao) {
    suspend fun getAll(): List<SavedPlaceWithLocations> = dao.getAllPlaces().map { place ->
        SavedPlaceWithLocations(place, dao.getLocations(place.id))
    }

    suspend fun getActiveLocation(placeId: String): SavedPlaceLocationEntity? {
        val place = dao.getPlace(placeId) ?: return null
        return place.activeLocationId?.let { dao.getLocation(it) }
    }

    suspend fun createPlace(
        name: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int
    ): SavedPlaceEntity {
        val place = SavedPlaceEntity(id = UUID.randomUUID().toString(), name = name.trim())
        dao.savePlace(place)
        addLocation(place.id, latitude, longitude, radiusMeters, name.trim())
        return dao.getPlace(place.id)!!
    }

    suspend fun addLocation(
        placeId: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        label: String? = null
    ): SavedPlaceLocationEntity {
        val existingCount = dao.getLocations(placeId).size
        val location = SavedPlaceLocationEntity(
            id = UUID.randomUUID().toString(),
            placeId = placeId,
            label = label?.trim()?.ifBlank { null } ?: "Position ${existingCount + 1}",
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters
        )
        dao.saveLocation(location)
        dao.setActiveLocation(placeId, location.id, System.currentTimeMillis())
        return location
    }

    suspend fun setActiveLocation(placeId: String, locationId: String) {
        require(dao.getLocation(locationId)?.placeId == placeId) { "Location does not belong to place" }
        dao.setActiveLocation(placeId, locationId, System.currentTimeMillis())
    }
}
