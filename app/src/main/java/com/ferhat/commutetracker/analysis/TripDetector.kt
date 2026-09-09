package com.ferhat.commutetracker.analysis

/** A stop the user made that did NOT end the trip (bus wait, traffic, quick pause). */
data class DetectedStop(
    val latitude: Double,
    val longitude: Double,
    val arrivalMillis: Long,
    val departureMillis: Long,
    val kind: String,
) {
    val durationMillis: Long get() = departureMillis - arrivalMillis
}

/** A journey the detector is confident about, before it's written to the database. */
data class DetectedTrip(
    val startMillis: Long,
    val endMillis: Long,
    val originLatitude: Double,
    val originLongitude: Double,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
    val originPlaceId: Long?,
    val destinationPlaceId: Long?,
    val pathMeters: Double,
    val netMeters: Double,
    val straightness: Double,
    val maxAccuracyMeters: Float,
    val pointCount: Int,
    val stops: List<DetectedStop>,
)

/**
 * Turns a cleaned GPS trace into [DetectedTrip]s using the anchor + dwell model:
 *
 *  - A **dwell** is a stretch where you stayed within [DetectionConfig.anchorRadiusMeters].
 *  - The first and last dwells of the trace, dwells at a known place, and dwells longer
 *    than the transit-wait tolerance are **anchors** (trip endpoints).
 *  - Shorter mid-journey dwells are **transit waits** — kept as [DetectedStop]s, they do
 *    not split the trip.
 *  - Movement between two anchors is a candidate trip; its start is nudged past any
 *    initial circling near the origin. It's kept only if it clears the distance /
 *    duration / straightness gates (rejects mailbox runs, dog walks, car-park loops).
 */
class TripDetector(private val config: DetectionConfig = DetectionConfig.DEFAULT) {

    private data class Dwell(
        val centroidLat: Double,
        val centroidLng: Double,
        val arrivalMillis: Long,
        val departureMillis: Long,
        val startIndex: Int,
        val endIndex: Int, // inclusive
    ) {
        val durationMillis: Long get() = departureMillis - arrivalMillis
    }

    fun detect(
        rawPoints: List<GpsPoint>,
        knownPlaces: List<KnownPlace>,
        fallbackOriginPlaceId: Long? = null,
    ): List<DetectedTrip> {
        val points = rawPoints.sortedBy { it.epochMillis }
        if (points.size < 3) return emptyList()

        val dwells = findDwells(points)
        val resolver = PlaceResolver(minMatchRadiusMeters = config.newPlaceRadiusMeters + 10.0)

        // Classify each dwell as an anchor (trip boundary) or a transit wait.
        val anchorFlags = dwells.mapIndexed { index, dwell ->
            val isEdge = index == 0 || index == dwells.lastIndex
            val matchedPlace = (resolver.resolve(dwell.centroidLat, dwell.centroidLng, knownPlaces) as? PlaceMatch.Existing)
                ?.let { match -> knownPlaces.firstOrNull { it.id == match.placeId } }
            val tolerance =
                if (matchedPlace?.isTransit == true) config.transitPlaceWaitToleranceMillis
                else config.transitWaitToleranceMillis
            val longEnough = dwell.durationMillis >= tolerance
            val atRealPlace = matchedPlace != null && !matchedPlace.isTransit
            isEdge || longEnough || atRealPlace
        }

        val anchorIndices = dwells.indices.filter { anchorFlags[it] }
        if (anchorIndices.size < 2) {
            // No clear origin+destination pair. If the trace is basically one big move
            // with no dwells at all, offer a single best-effort trip.
            return if (dwells.isEmpty()) {
                listOfNotNull(buildOpenTrip(points, knownPlaces, resolver, fallbackOriginPlaceId))
            } else {
                emptyList()
            }
        }

        val trips = mutableListOf<DetectedTrip>()
        for (k in 0 until anchorIndices.size - 1) {
            val originDwell = dwells[anchorIndices[k]]
            val destDwell = dwells[anchorIndices[k + 1]]
            val transitDwells = ((anchorIndices[k] + 1) until anchorIndices[k + 1]).map { dwells[it] }
            buildTrip(points, originDwell, destDwell, transitDwells, knownPlaces, resolver)
                ?.let(trips::add)
        }
        return trips
    }

    // ---- dwell detection ----------------------------------------------

    private fun findDwells(points: List<GpsPoint>): List<Dwell> {
        val dwells = mutableListOf<Dwell>()
        val minDwellMillis = 90_000L
        var i = 0
        val n = points.size
        while (i < n) {
            var j = i + 1
            while (j < n && GeoMath.distanceMeters(
                    points[i].latitude, points[i].longitude,
                    points[j].latitude, points[j].longitude,
                ) <= config.anchorRadiusMeters
            ) {
                j++
            }
            val span = points[j - 1].epochMillis - points[i].epochMillis
            if (span >= minDwellMillis) {
                val cluster = points.subList(i, j)
                val (lat, lng) = GeoMath.centroid(cluster.map { it.latitude to it.longitude })
                dwells += Dwell(lat, lng, cluster.first().epochMillis, cluster.last().epochMillis, i, j - 1)
                i = j
            } else {
                i++
            }
        }
        return dwells
    }

    // ---- trip assembly ----------------------------------------------

    private fun buildTrip(
        points: List<GpsPoint>,
        origin: Dwell,
        destination: Dwell,
        transitDwells: List<Dwell>,
        knownPlaces: List<KnownPlace>,
        resolver: PlaceResolver,
    ): DetectedTrip? {
        var startMillis = origin.departureMillis
        val endMillis = destination.arrivalMillis
        if (endMillis - startMillis < config.minTripDurationMillis) return null

        // Nudge the start past any initial circling near the origin.
        startMillis = commitStart(points, origin, endMillis) ?: startMillis

        val travelPoints = points.filter { it.epochMillis in startMillis..endMillis }
        if (travelPoints.size < 2) return null

        // Path length excludes time spent parked at a transit stop.
        val stopWindows = transitDwells.map { it.arrivalMillis..it.departureMillis }
        val movingPoints = travelPoints.filter { p -> stopWindows.none { p.epochMillis in it } }
        if (movingPoints.size < 2) return null

        val pathMeters = TrackMetrics.pathLengthMeters(movingPoints)
        val netMeters = GeoMath.distanceMeters(
            origin.centroidLat, origin.centroidLng, destination.centroidLat, destination.centroidLng,
        )
        val straightness = if (pathMeters > 0) (netMeters / pathMeters).coerceIn(0.0, 1.0) else 1.0

        if (netMeters < config.minTripNetDistanceMeters) return null
        if (pathMeters < config.minTripPathMeters) return null
        if (straightness < config.circlingStraightness) return null

        val stops = transitDwells.map { dwell ->
            val atTransitPlace = knownPlaces.any { place ->
                place.isTransit && GeoMath.distanceMeters(
                    place.latitude, place.longitude, dwell.centroidLat, dwell.centroidLng,
                ) <= maxOf(place.radiusMeters, config.newPlaceRadiusMeters)
            }
            DetectedStop(
                latitude = dwell.centroidLat,
                longitude = dwell.centroidLng,
                arrivalMillis = dwell.arrivalMillis,
                departureMillis = dwell.departureMillis,
                kind = if (atTransitPlace) TripStopKind.TRANSIT else TripStopKind.WAIT,
            )
        }

        return DetectedTrip(
            startMillis = startMillis,
            endMillis = endMillis,
            originLatitude = origin.centroidLat,
            originLongitude = origin.centroidLng,
            destinationLatitude = destination.centroidLat,
            destinationLongitude = destination.centroidLng,
            originPlaceId = (resolver.resolve(origin.centroidLat, origin.centroidLng, knownPlaces) as? PlaceMatch.Existing)?.placeId,
            destinationPlaceId = (resolver.resolve(destination.centroidLat, destination.centroidLng, knownPlaces) as? PlaceMatch.Existing)?.placeId,
            pathMeters = pathMeters,
            netMeters = netMeters,
            straightness = straightness,
            maxAccuracyMeters = TrackMetrics.maxAccuracyMeters(travelPoints),
            pointCount = travelPoints.size,
            stops = stops,
        )
    }

    /**
     * Finds the moment sustained outward travel really began, so the trip start isn't
     * polluted by a few minutes of milling around near the origin.
     */
    private fun commitStart(points: List<GpsPoint>, origin: Dwell, endMillis: Long): Long? {
        val after = points.filter { it.epochMillis in origin.departureMillis..endMillis }
        if (after.size < 3) return null
        val commitFloor = config.commitDistanceMeters * 0.3
        for (index in after.indices) {
            val p = after[index]
            val distFromOrigin = GeoMath.distanceMeters(
                origin.centroidLat, origin.centroidLng, p.latitude, p.longitude,
            )
            if (distFromOrigin < commitFloor) continue
            val ahead = after.subList(index, after.size)
            if (TrackMetrics.straightness(ahead) >= config.circlingStraightness) {
                return p.epochMillis
            }
        }
        return null
    }

    private fun buildOpenTrip(
        points: List<GpsPoint>,
        knownPlaces: List<KnownPlace>,
        resolver: PlaceResolver,
        fallbackOriginPlaceId: Long?,
    ): DetectedTrip? {
        if (points.size < 2) return null
        val first = points.first()
        val last = points.last()
        val pathMeters = TrackMetrics.pathLengthMeters(points)
        val netMeters = GeoMath.distanceMeters(first.latitude, first.longitude, last.latitude, last.longitude)
        if (netMeters < config.minTripNetDistanceMeters || pathMeters < config.minTripPathMeters) return null
        if (last.epochMillis - first.epochMillis < config.minTripDurationMillis) return null
        val straightness = if (pathMeters > 0) (netMeters / pathMeters).coerceIn(0.0, 1.0) else 1.0
        if (straightness < config.circlingStraightness) return null

        return DetectedTrip(
            startMillis = first.epochMillis,
            endMillis = last.epochMillis,
            originLatitude = first.latitude,
            originLongitude = first.longitude,
            destinationLatitude = last.latitude,
            destinationLongitude = last.longitude,
            originPlaceId = (resolver.resolve(first.latitude, first.longitude, knownPlaces) as? PlaceMatch.Existing)?.placeId
                ?: fallbackOriginPlaceId,
            destinationPlaceId = (resolver.resolve(last.latitude, last.longitude, knownPlaces) as? PlaceMatch.Existing)?.placeId,
            pathMeters = pathMeters,
            netMeters = netMeters,
            straightness = straightness,
            maxAccuracyMeters = TrackMetrics.maxAccuracyMeters(points),
            pointCount = points.size,
            stops = emptyList(),
        )
    }
}

object TripStopKind {
    const val WAIT = "wait"
    const val TRANSIT = "transit"
}
