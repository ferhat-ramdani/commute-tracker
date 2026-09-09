package com.ferhat.commutetracker.analysis

/** A single location sample fed to the detector. Decoupled from Room so it unit-tests cleanly. */
data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val epochMillis: Long,
    val accuracyMeters: Float = 0f,
)
