package com.ferhat.commutetracker.analysis

/**
 * Helpers to synthesise GPS traces for the detector tests. Coordinates are around a
 * fixed origin; 0.0009° of latitude ≈ 100 m.
 */
object TrajectoryFixtures {

    const val HOME_LAT = 36.7500
    const val HOME_LNG = 3.0600

    /** ~2 km north of home. */
    const val WORK_LAT = 36.7680
    const val WORK_LNG = 3.0600

    /** ~1 km east of home. */
    const val MOSQUE_LAT = 36.7500
    const val MOSQUE_LNG = 3.0712

    /** ~55 m from home (the "next shop" case). */
    const val SHOP_LAT = 36.7505
    const val SHOP_LNG = 3.0600

    private const val METER_DEG = 1.0 / 111_320.0
    private const val SECOND = 1_000L

    /** Fixes jittering around a point, one every 30 s, for [minutes]. */
    fun dwell(lat: Double, lng: Double, startMillis: Long, minutes: Int, jitterMeters: Double = 8.0): List<GpsPoint> {
        val steps = (minutes * 2).coerceAtLeast(1)
        return (0..steps).map { i ->
            val sign = if (i % 2 == 0) 1 else -1
            GpsPoint(
                latitude = lat + sign * jitterMeters * METER_DEG,
                longitude = lng + sign * jitterMeters * METER_DEG,
                epochMillis = startMillis + i * 30 * SECOND,
                accuracyMeters = 10f,
            )
        }
    }

    /** Straight-line travel A→B, a fix every 30 s, taking [minutes]. */
    fun move(
        fromLat: Double, fromLng: Double,
        toLat: Double, toLng: Double,
        startMillis: Long, minutes: Int,
    ): List<GpsPoint> {
        val steps = (minutes * 2).coerceAtLeast(2)
        return (1..steps).map { i ->
            val t = i.toDouble() / steps
            GpsPoint(
                latitude = fromLat + (toLat - fromLat) * t,
                longitude = fromLng + (toLng - fromLng) * t,
                epochMillis = startMillis + i * 30 * SECOND,
                accuracyMeters = 14f,
            )
        }
    }

    /** Travel through a series of waypoints (for loops / winding routes). */
    fun path(waypoints: List<Pair<Double, Double>>, startMillis: Long, minutesPerLeg: Int): List<GpsPoint> {
        val points = mutableListOf<GpsPoint>()
        var t = startMillis
        for (i in 1 until waypoints.size) {
            val leg = move(
                waypoints[i - 1].first, waypoints[i - 1].second,
                waypoints[i].first, waypoints[i].second,
                t, minutesPerLeg,
            )
            points += leg
            t = leg.last().epochMillis + 30 * SECOND
        }
        return points
    }

    fun offset(lat: Double, lng: Double, northMeters: Double, eastMeters: Double): Pair<Double, Double> =
        (lat + northMeters * METER_DEG) to (lng + eastMeters * METER_DEG / Math.cos(Math.toRadians(lat)))

    /** Home → Work, no stops. */
    fun homeToWork(startMillis: Long = 0L): List<GpsPoint> {
        val home = dwell(HOME_LAT, HOME_LNG, startMillis, 15)
        var t = home.last().epochMillis + 30 * SECOND
        val transit = move(HOME_LAT, HOME_LNG, WORK_LAT, WORK_LNG, t, 12)
        t = transit.last().epochMillis + 30 * SECOND
        val work = dwell(WORK_LAT, WORK_LNG, t, 15)
        return home + transit + work
    }
}
