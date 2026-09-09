package com.ferhat.commutetracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The raw "where was I at time T" history — every location fix the app sees, plus a
 * sparse trickle while stationary. This is the base layer the trip detector runs over.
 * Kept in full for a retention window, then thinned, then deleted.
 */
@Entity(tableName = "position_log", indices = [Index("epochMillis")])
data class PositionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMps: Float,
    val bearingDeg: Float,
    val epochMillis: Long,
    /** [SOURCE_TRIP], [SOURCE_TRANSITION] or [SOURCE_HEARTBEAT]. */
    val source: String,
) {
    companion object {
        const val SOURCE_TRIP = "trip"
        const val SOURCE_TRANSITION = "transition"
        const val SOURCE_HEARTBEAT = "heartbeat"
    }
}
