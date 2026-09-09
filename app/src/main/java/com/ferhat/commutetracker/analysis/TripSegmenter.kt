package com.ferhat.commutetracker.analysis

/** A movement segment between two stops, before place resolution. */
data class RawTrip(
    val startMillis: Long,
    val endMillis: Long,
    val originStay: StayPoint?,
    val destinationStay: StayPoint?,
    val distanceMeters: Double,
    val pointCount: Int,
) {
    val durationMillis: Long get() = endMillis - startMillis
}

/**
 * Splits a GPS trace into [RawTrip]s using the [StayPoint]s already detected in it.
 * A trip is the movement between two consecutive stops (plus optional leading/trailing
 * movement when the trace begins or ends mid-journey).
 *
 * Segments shorter than [minDistanceMeters] or [minDurationMillis] are dropped as noise.
 */
class TripSegmenter(
    private val minDistanceMeters: Double = 150.0,
    private val minDurationMillis: Long = 120_000L,
) {
    fun segment(points: List<GpsPoint>, stayPoints: List<StayPoint>): List<RawTrip> {
        val trace = points.sortedBy { it.epochMillis }
        if (trace.size < 2) return emptyList()

        if (stayPoints.isEmpty()) {
            return listOfNotNull(
                buildTrip(trace, trace.first().epochMillis, trace.last().epochMillis, null, null),
            )
        }

        val ordered = stayPoints.sortedBy { it.arrivalMillis }
        val trips = mutableListOf<RawTrip>()

        // Leading movement: trace start -> first stop.
        buildTrip(trace, trace.first().epochMillis, ordered.first().arrivalMillis, null, ordered.first())
            ?.let(trips::add)

        // Movement between consecutive stops.
        for (k in 0 until ordered.size - 1) {
            buildTrip(
                trace,
                ordered[k].departureMillis,
                ordered[k + 1].arrivalMillis,
                ordered[k],
                ordered[k + 1],
            )?.let(trips::add)
        }

        // Trailing movement: last stop -> trace end.
        buildTrip(trace, ordered.last().departureMillis, trace.last().epochMillis, ordered.last(), null)
            ?.let(trips::add)

        return trips
    }

    private fun buildTrip(
        trace: List<GpsPoint>,
        startMillis: Long,
        endMillis: Long,
        origin: StayPoint?,
        destination: StayPoint?,
    ): RawTrip? {
        if (endMillis - startMillis < minDurationMillis) return null
        val window = trace.filter { it.epochMillis in startMillis..endMillis }
        if (window.size < 2) return null
        val distance = GeoMath.pathLengthMeters(window.map { it.latitude to it.longitude })
        if (distance < minDistanceMeters) return null
        return RawTrip(
            startMillis = startMillis,
            endMillis = endMillis,
            originStay = origin,
            destinationStay = destination,
            distanceMeters = distance,
            pointCount = window.size,
        )
    }
}
