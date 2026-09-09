package com.ferhat.commutetracker.analysis

/**
 * Every tunable in the trip detector, in one place. These are the defaults the user
 * agreed to; change them here.
 */
data class DetectionConfig(
    // ---- fix rejection ----------------------------------------------------
    /** Drop fixes less accurate than this (metres). */
    val maxAccuracyMeters: Float = 40f,
    /** Drop a fix implying more than this speed vs. the previous good fix (m/s ≈ 216 km/h). */
    val maxPlausibleSpeedMps: Double = 60.0,

    // ---- anchors / "at a place" ----------------------------------------
    /** A run of fixes staying within this radius of their centroid is a dwell. */
    val anchorRadiusMeters: Double = 40.0,
    /** …for at least this long to form / re-enter an anchor. */
    val anchorMinDwellMillis: Long = 12 * 60_000L,
    /** Default match radius for a freshly discovered place. */
    val newPlaceRadiusMeters: Double = 35.0,
    val minPlaceRadiusMeters: Double = 15.0,
    val maxPlaceRadiusMeters: Double = 60.0,

    // ---- leaving an anchor -------------------------------------------
    /** Must be this far past the anchor's radius, sustained, to count as "leaving". */
    val leaveMarginMeters: Double = 25.0,
    val leaveSustainMillis: Long = 75_000L,
    /** Give up on a "leaving" that hasn't committed after this long → it was local pottering. */
    val leavingAbortMillis: Long = 10 * 60_000L,

    // ---- committing to a trip -------------------------------------
    /** Straightness (netDisplacement / pathLength) at or above this = travelling, not circling. */
    val commitStraightness: Double = 0.70,
    /** …and at least this far from the origin anchor. */
    val commitDistanceMeters: Double = 300.0,
    /** Straightness below this while not getting far = circling → abort. */
    val circlingStraightness: Double = 0.40,

    // ---- stops during a trip ------------------------------------
    /** Near-zero speed for this long → a possible stop. */
    val possibleStopAfterMillis: Long = 2 * 60_000L,
    val stoppedSpeedMps: Double = 0.6,
    /** A mid-trip stop shorter than this is a transit wait, not a destination. */
    val transitWaitToleranceMillis: Long = 20 * 60_000L,
    /** …extended to this when the stop is at a place marked as transit. */
    val transitPlaceWaitToleranceMillis: Long = 45 * 60_000L,

    // ---- finalising a trip ------------------------------------
    /** Keep a trip only if the origin→destination straight-line distance is at least this. */
    val minTripNetDistanceMeters: Double = 600.0,
    /** …and the travelled path is at least this. */
    val minTripPathMeters: Double = 700.0,
    val minTripDurationMillis: Long = 3 * 60_000L,

    // ---- sliding window ---------------------------------------
    val windowMillis: Long = 4 * 60_000L,
    val windowMinPoints: Int = 4,

    // ---- position log retention ------------------------------
    val positionLogFullRetentionDays: Int = 90,
    /** After the full-retention window, thin the log to at most one fix per this gap. */
    val positionLogDownsampleGapMillis: Long = 60 * 60_000L,
) {
    companion object {
        val DEFAULT = DetectionConfig()
    }
}
