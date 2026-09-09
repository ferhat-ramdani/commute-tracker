package com.ferhat.commutetracker.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class PlaceRepository(context: Context) {
    private val db = AppDatabase.get(context)
    private val placeDao = db.placeDao()
    private val tripDao = db.tripDao()
    private val wifiDao = db.placeWifiSignatureDao()

    val places: Flow<List<Place>> = placeDao.observeAll()

    suspend fun getById(id: Long): Place? = placeDao.getById(id)

    suspend fun getLocatedPlaces(): List<Place> = placeDao.getLocated()

    suspend fun addUserPlace(label: String, latitude: Double? = null, longitude: Double? = null): Long {
        val trimmed = label.trim()
        require(trimmed.isNotEmpty()) { "Place label must not be blank" }
        return placeDao.insert(
            Place(
                label = trimmed,
                latitude = latitude,
                longitude = longitude,
                isConfirmed = true,
                source = Place.SOURCE_USER,
            ),
        )
    }

    /** Inserts a place discovered by the analyser. */
    suspend fun addAutoPlace(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double = Place.DEFAULT_RADIUS_METERS,
        seenAt: Long = System.currentTimeMillis(),
    ): Long = placeDao.insert(
        Place(
            label = "New place",
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            isConfirmed = false,
            visitCount = 1,
            firstSeenAt = seenAt,
            lastSeenAt = seenAt,
            source = Place.SOURCE_AUTO,
        ),
    )

    suspend fun rename(place: Place, label: String) {
        val trimmed = label.trim()
        if (trimmed.isNotEmpty()) placeDao.update(place.copy(label = trimmed, isConfirmed = true))
    }

    suspend fun setCoordinates(place: Place, latitude: Double, longitude: Double) {
        placeDao.update(place.copy(latitude = latitude, longitude = longitude))
    }

    suspend fun setRadius(place: Place, radiusMeters: Double) {
        placeDao.update(place.copy(radiusMeters = radiusMeters.coerceIn(15.0, 500.0)))
    }

    suspend fun setCategory(place: Place, category: String?) {
        placeDao.update(place.copy(category = category))
    }

    suspend fun setKind(place: Place, kind: String) {
        placeDao.update(place.copy(kind = kind))
    }

    suspend fun confirm(place: Place) {
        placeDao.update(place.copy(isConfirmed = true))
    }

    suspend fun recordVisit(placeId: Long, at: Long = System.currentTimeMillis()) {
        placeDao.recordVisit(placeId, at)
    }

    suspend fun delete(place: Place) = placeDao.delete(place)

    /**
     * Merges [from] into [keep]: every trip endpoint pointing at [from] is repointed at
     * [keep], then [from] is deleted.
     */
    suspend fun merge(keep: Place, from: Place) {
        if (keep.id == from.id) return
        tripDao.getFinished().forEach { trip ->
            val newOrigin = if (trip.originPlaceId == from.id) keep.id else trip.originPlaceId
            val newDest = if (trip.destinationPlaceId == from.id) keep.id else trip.destinationPlaceId
            if (newOrigin != trip.originPlaceId || newDest != trip.destinationPlaceId) {
                tripDao.update(trip.copy(originPlaceId = newOrigin, destinationPlaceId = newDest))
            }
        }
        placeDao.update(
            keep.copy(
                visitCount = keep.visitCount + from.visitCount,
                firstSeenAt = minOf(keep.firstSeenAt, from.firstSeenAt),
                lastSeenAt = maxOf(keep.lastSeenAt, from.lastSeenAt),
            ),
        )
        placeDao.delete(from) // cascades wifi signatures
    }

    suspend fun getWifiSignatures(placeId: Long): List<PlaceWifiSignature> = wifiDao.getForPlace(placeId)
}
