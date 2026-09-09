package com.ferhat.commutetracker.analysis

/** Minimal view of a place for matching. Decoupled from Room for testing. */
data class KnownPlace(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
)

sealed interface PlaceMatch {
    /** Coordinates fall inside an existing place. */
    data class Existing(val placeId: Long) : PlaceMatch

    /** No existing place is close enough; caller should create one here. */
    data class New(val latitude: Double, val longitude: Double) : PlaceMatch
}

/**
 * Phase A place resolution: nearest-known-place by distance, matching when the point is
 * within that place's radius (with a floor to absorb GPS error). Phase B adds Wi-Fi
 * fingerprint matching in front of this.
 */
class PlaceResolver(
    private val minMatchRadiusMeters: Double = 45.0,
) {
    fun resolve(latitude: Double, longitude: Double, knownPlaces: List<KnownPlace>): PlaceMatch {
        val nearest = knownPlaces
            .map { it to GeoMath.distanceMeters(latitude, longitude, it.latitude, it.longitude) }
            .minByOrNull { it.second }

        if (nearest != null) {
            val (place, distance) = nearest
            val threshold = maxOf(place.radiusMeters, minMatchRadiusMeters)
            if (distance <= threshold) return PlaceMatch.Existing(place.id)
        }
        return PlaceMatch.New(latitude, longitude)
    }
}
