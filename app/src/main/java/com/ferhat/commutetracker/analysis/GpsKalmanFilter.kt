package com.ferhat.commutetracker.analysis

import kotlin.math.sqrt

/**
 * The well-known lightweight 1-D-per-axis Kalman filter for smoothing a GPS track
 * (position only, constant-velocity model folded into a single process-noise term).
 * [processNoiseMps] is roughly how fast the true position can change between updates —
 * ~1 m/s for walking, ~3 for driving; 2.0 is a reasonable all-round default.
 *
 * Feed fixes in time order; each call returns the smoothed lat/lng.
 */
class GpsKalmanFilter(private val processNoiseMps: Double = 2.0) {

    private var lat = 0.0
    private var lng = 0.0
    private var variance = -1.0 // metres^2; <0 means "not initialised"
    private var lastMillis = 0L

    fun reset() {
        variance = -1.0
    }

    /** @return smoothed (latitude, longitude). */
    fun process(latitude: Double, longitude: Double, accuracyMeters: Float, epochMillis: Long): Pair<Double, Double> {
        val accuracy = accuracyMeters.coerceAtLeast(1f).toDouble()
        if (variance < 0) {
            lat = latitude
            lng = longitude
            variance = accuracy * accuracy
            lastMillis = epochMillis
            return lat to lng
        }

        val dtSeconds = ((epochMillis - lastMillis).coerceAtLeast(0L)) / 1000.0
        if (dtSeconds > 0) {
            variance += dtSeconds * processNoiseMps * processNoiseMps
            lastMillis = epochMillis
        }

        val kalmanGain = variance / (variance + accuracy * accuracy)
        lat += kalmanGain * (latitude - lat)
        lng += kalmanGain * (longitude - lng)
        variance *= (1 - kalmanGain)
        return lat to lng
    }

    val estimatedAccuracyMeters: Double get() = if (variance < 0) Double.NaN else sqrt(variance)
}
