package com.ferhat.commutetracker.analysis

import kotlin.math.sqrt

/** Window statistics used by the trip detector. All operate on a time-ordered sublist. */
object TrackMetrics {

    fun netDistanceMeters(points: List<GpsPoint>): Double {
        if (points.size < 2) return 0.0
        val a = points.first()
        val b = points.last()
        return GeoMath.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
    }

    fun pathLengthMeters(points: List<GpsPoint>): Double =
        GeoMath.pathLengthMeters(points.map { it.latitude to it.longitude })

    /** netDistance / pathLength — 1.0 is a straight line, →0 is circling. */
    fun straightness(points: List<GpsPoint>): Double {
        val path = pathLengthMeters(points)
        if (path <= 0.0) return 1.0
        return (netDistanceMeters(points) / path).coerceIn(0.0, 1.0)
    }

    /** RMS distance of the points from their centroid — how "spread out" they are. */
    fun radiusOfGyrationMeters(points: List<GpsPoint>): Double {
        if (points.size < 2) return 0.0
        val (cLat, cLng) = GeoMath.centroid(points.map { it.latitude to it.longitude })
        val sumSq = points.sumOf {
            val d = GeoMath.distanceMeters(cLat, cLng, it.latitude, it.longitude)
            d * d
        }
        return sqrt(sumSq / points.size)
    }

    /** Median speed (m/s) implied by consecutive fixes, ignoring the reported speed field. */
    fun medianSpeedMps(points: List<GpsPoint>): Double {
        if (points.size < 2) return 0.0
        val speeds = ArrayList<Double>(points.size - 1)
        for (i in 1 until points.size) {
            val seconds = (points[i].epochMillis - points[i - 1].epochMillis) / 1000.0
            if (seconds <= 0.0) continue
            speeds += GeoMath.distanceMeters(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude,
            ) / seconds
        }
        if (speeds.isEmpty()) return 0.0
        speeds.sort()
        return speeds[speeds.size / 2]
    }

    fun maxAccuracyMeters(points: List<GpsPoint>): Float =
        points.maxOfOrNull { it.accuracyMeters } ?: 0f
}
