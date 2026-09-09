package com.ferhat.commutetracker.analysis

import com.ferhat.commutetracker.data.Place
import com.ferhat.commutetracker.data.RouteLabel
import com.ferhat.commutetracker.data.Trip

/** One from-to route with aggregate stats over all trips on it. */
data class RouteGroup(
    val originPlaceId: Long,
    val destinationPlaceId: Long,
    val originLabel: String,
    val destinationLabel: String,
    val label: String,
    val category: String?,
    val tripCount: Int,
    val medianDurationMillis: Long,
    val p90DurationMillis: Long,
    val shortestDurationMillis: Long,
    val longestDurationMillis: Long,
    val totalDurationMillis: Long,
    val lastTripEndMillis: Long,
    /** Count of trips per hour-of-day (0..23), for a simple "when do you do this" view. */
    val hourHistogram: IntArray,
    val trips: List<Trip>,
) {
    override fun equals(other: Any?) = other is RouteGroup &&
        originPlaceId == other.originPlaceId && destinationPlaceId == other.destinationPlaceId
    override fun hashCode() = 31 * originPlaceId.hashCode() + destinationPlaceId.hashCode()
}

/**
 * Groups finished, fully-resolved trips by (origin, destination) place pair and computes
 * per-route statistics. Pure function — the ViewModel recomputes it whenever trips,
 * places or labels change.
 */
object TripGrouper {

    fun group(
        trips: List<Trip>,
        placesById: Map<Long, Place>,
        routeLabels: Map<Pair<Long, Long>, RouteLabel>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<RouteGroup> {
        return trips
            .asSequence()
            .filter { it.endEpochMillis != null }
            .filter { it.originPlaceId != null && it.destinationPlaceId != null }
            .filter { it.originPlaceId != it.destinationPlaceId }
            .groupBy { it.originPlaceId!! to it.destinationPlaceId!! }
            .map { (key, groupTrips) -> buildGroup(key, groupTrips, placesById, routeLabels, nowMillis) }
            .sortedWith(compareByDescending<RouteGroup> { it.tripCount }.thenByDescending { it.lastTripEndMillis })
    }

    private fun buildGroup(
        key: Pair<Long, Long>,
        groupTrips: List<Trip>,
        placesById: Map<Long, Place>,
        routeLabels: Map<Pair<Long, Long>, RouteLabel>,
        nowMillis: Long,
    ): RouteGroup {
        val (originId, destId) = key
        val originLabel = placesById[originId]?.label ?: "Unknown"
        val destLabel = placesById[destId]?.label ?: "Unknown"
        val routeLabel = routeLabels[key]

        val durations = groupTrips.map { it.durationMillis(nowMillis) }.sorted()
        val hist = IntArray(24)
        groupTrips.forEach { trip ->
            val hour = ((trip.startEpochMillis / 3_600_000L) % 24L).toInt()
            hist[hour]++
        }

        return RouteGroup(
            originPlaceId = originId,
            destinationPlaceId = destId,
            originLabel = originLabel,
            destinationLabel = destLabel,
            label = routeLabel?.label ?: "$originLabel → $destLabel",
            category = routeLabel?.category,
            tripCount = groupTrips.size,
            medianDurationMillis = percentile(durations, 0.5),
            p90DurationMillis = percentile(durations, 0.9),
            shortestDurationMillis = durations.first(),
            longestDurationMillis = durations.last(),
            totalDurationMillis = durations.sum(),
            lastTripEndMillis = groupTrips.maxOf { it.endEpochMillis ?: it.startEpochMillis },
            hourHistogram = hist,
            trips = groupTrips.sortedByDescending { it.startEpochMillis },
        )
    }

    /** Nearest-rank percentile on an already-sorted, non-empty list. */
    private fun percentile(sorted: List<Long>, p: Double): Long {
        if (sorted.isEmpty()) return 0
        val rank = Math.ceil(p * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }
}
