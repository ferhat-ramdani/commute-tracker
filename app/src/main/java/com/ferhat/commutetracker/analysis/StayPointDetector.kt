package com.ferhat.commutetracker.analysis

/** A point sample fed to the detector. Decoupled from Room so it can be unit-tested. */
data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val epochMillis: Long,
    val accuracyMeters: Float = 0f,
)

/** A place where the user lingered: [centroid] plus the time window they were there. */
data class StayPoint(
    val latitude: Double,
    val longitude: Double,
    val arrivalMillis: Long,
    val departureMillis: Long,
    val pointCount: Int,
) {
    val dwellMillis: Long get() = departureMillis - arrivalMillis
}

/**
 * Detects stay points in a time-ordered GPS trace using the classic rule-based method
 * (Li et al. 2008): a stay point is a maximal run of consecutive samples that never
 * strays more than [distanceThresholdMeters] from the run's first sample, provided the
 * run spans at least [timeThresholdMillis].
 *
 * Everything between two stay points is movement (a trip).
 */
class StayPointDetector(
    private val distanceThresholdMeters: Double = 60.0,
    private val timeThresholdMillis: Long = 5 * 60 * 1000L,
    private val maxAccuracyMeters: Float = 100f,
) {
    fun detect(points: List<GpsPoint>): List<StayPoint> {
        val trace = points
            .filter { it.accuracyMeters <= 0f || it.accuracyMeters <= maxAccuracyMeters }
            .sortedBy { it.epochMillis }
        if (trace.size < 2) return emptyList()

        val stayPoints = mutableListOf<StayPoint>()
        var i = 0
        val n = trace.size
        while (i < n) {
            var j = i + 1
            var anchorBroken = false
            while (j < n) {
                val dist = GeoMath.distanceMeters(
                    trace[i].latitude, trace[i].longitude,
                    trace[j].latitude, trace[j].longitude,
                )
                if (dist > distanceThresholdMeters) {
                    anchorBroken = true
                    break
                }
                j++
            }
            // Cluster is trace[i until j].
            val spanMillis = trace[j - 1].epochMillis - trace[i].epochMillis
            if (spanMillis >= timeThresholdMillis) {
                val cluster = trace.subList(i, j)
                val (lat, lon) = GeoMath.centroid(cluster.map { it.latitude to it.longitude })
                stayPoints += StayPoint(
                    latitude = lat,
                    longitude = lon,
                    arrivalMillis = cluster.first().epochMillis,
                    departureMillis = cluster.last().epochMillis,
                    pointCount = cluster.size,
                )
            }
            i = if (anchorBroken) j else n
        }
        return stayPoints
    }
}
