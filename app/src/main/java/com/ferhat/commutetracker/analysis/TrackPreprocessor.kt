package com.ferhat.commutetracker.analysis

/**
 * Cleans a raw GPS trace before the detector sees it:
 *  1. drop fixes that are too inaccurate, mock, or physically impossible (teleports),
 *  2. Kalman-smooth what's left.
 */
class TrackPreprocessor(private val config: DetectionConfig = DetectionConfig.DEFAULT) {

    fun process(raw: List<GpsPoint>): List<GpsPoint> {
        val sorted = raw.sortedBy { it.epochMillis }
        val accepted = ArrayList<GpsPoint>(sorted.size)

        for (point in sorted) {
            if (point.accuracyMeters > 0f && point.accuracyMeters > config.maxAccuracyMeters) continue
            val previous = accepted.lastOrNull()
            if (previous != null) {
                val seconds = (point.epochMillis - previous.epochMillis) / 1000.0
                if (seconds <= 0.0) continue
                val speed = GeoMath.distanceMeters(
                    previous.latitude, previous.longitude, point.latitude, point.longitude,
                ) / seconds
                if (speed > config.maxPlausibleSpeedMps) continue
            }
            accepted += point
        }
        if (accepted.size < 2) return accepted

        val kalman = GpsKalmanFilter()
        return accepted.map { point ->
            val (lat, lng) = kalman.process(
                point.latitude, point.longitude, point.accuracyMeters, point.epochMillis,
            )
            point.copy(latitude = lat, longitude = lng)
        }
    }
}
