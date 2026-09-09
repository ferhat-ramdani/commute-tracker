package com.ferhat.commutetracker.analysis

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Small geospatial helpers. All distances in metres, all angles in degrees. */
object GeoMath {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Great-circle distance between two lat/lng points, in metres. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    /**
     * Mean of a set of lat/lng points. Good enough at the scale of a single stay point
     * (tens of metres); no need for spherical averaging.
     */
    fun centroid(points: List<Pair<Double, Double>>): Pair<Double, Double> {
        require(points.isNotEmpty())
        var lat = 0.0
        var lon = 0.0
        for ((la, lo) in points) {
            lat += la
            lon += lo
        }
        return lat / points.size to lon / points.size
    }

    /** Total path length of an ordered list of lat/lng points, in metres. */
    fun pathLengthMeters(points: List<Pair<Double, Double>>): Double {
        var total = 0.0
        for (i in 1 until points.size) {
            total += distanceMeters(
                points[i - 1].first, points[i - 1].second,
                points[i].first, points[i].second,
            )
        }
        return total
    }
}
