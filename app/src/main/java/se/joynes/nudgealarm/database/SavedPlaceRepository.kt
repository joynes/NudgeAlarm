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

    suspend fun renamePlace(placeId: String, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Place name is required" }
        val place = requireNotNull(dao.getPlace(placeId)) { "Saved place not found" }
        dao.updatePlace(place.copy(name = trimmed, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updatePosition(
        placeId: String,
        locationId: String,
        label: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int
    ) {
        require(label.isNotBlank()) { "Position label is required" }
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "Invalid coordinates" }
        require(radiusMeters in 1..100_000) { "Invalid radius" }
        val location = requireNotNull(dao.getLocation(locationId)) { "Saved position not found" }
        require(location.placeId == placeId) { "Position does not belong to place" }
        dao.updateLocation(location.copy(
            label = label.trim(),
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters
        ))
    }

    suspend fun linkedReminderCount(placeId: String): Int = dao.getLinkedReminderCount(placeId)

    suspend fun deletePlace(placeId: String) = dao.deletePlaceAndUnlink(placeId)

    suspend fun deletePosition(placeId: String, locationId: String) = dao.deletePosition(placeId, locationId)
}
