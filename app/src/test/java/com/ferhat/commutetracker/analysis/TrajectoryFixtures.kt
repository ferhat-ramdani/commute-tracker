package com.ferhat.commutetracker.analysis

/** Helpers to synthesise GPS traces for the analysis unit tests. */
object TrajectoryFixtures {

    const val HOME_LAT = 36.7500
    const val HOME_LNG = 3.0600
    const val WORK_LAT = 36.7680 // ~2 km north
    const val WORK_LNG = 3.0600
    const val SHOP_LAT = 36.7505 // ~55 m from home
    const val SHOP_LNG = 3.0600

    private const val MINUTE = 60_000L

    /** N samples jittering around a fixed point, one per minute. */
    fun dwell(
        lat: Double,
        lng: Double,
        startMillis: Long,
        minutes: Int,
        jitterDegrees: Double = 0.0001,
    ): List<GpsPoint> = (0..minutes).map { i ->
        val sign = if (i % 2 == 0) 1 else -1
        GpsPoint(
            latitude = lat + sign * jitterDegrees,
            longitude = lng + sign * jitterDegrees,
            epochMillis = startMillis + i * MINUTE,
            accuracyMeters = 12f,
        )
    }

    /** Straight-line movement from A to B, one sample per minute. */
    fun move(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
        startMillis: Long,
        minutes: Int,
    ): List<GpsPoint> = (1 until minutes).map { i ->
        val t = i.toDouble() / minutes
        GpsPoint(
            latitude = fromLat + (toLat - fromLat) * t,
            longitude = fromLng + (toLng - fromLng) * t,
            epochMillis = startMillis + i * MINUTE,
            accuracyMeters = 15f,
        )
    }

    /** A full "home -> work" journey: dwell, move, dwell. */
    fun homeToWork(startMillis: Long = 0L): List<GpsPoint> {
        val home = dwell(HOME_LAT, HOME_LNG, startMillis, minutes = 20)
        val moveStart = home.last().epochMillis + MINUTE
        val transit = move(HOME_LAT, HOME_LNG, WORK_LAT, WORK_LNG, moveStart, minutes = 15)
        val workStart = transit.last().epochMillis + MINUTE
        val work = dwell(WORK_LAT, WORK_LNG, workStart, minutes = 20)
        return home + transit + work
    }
}
