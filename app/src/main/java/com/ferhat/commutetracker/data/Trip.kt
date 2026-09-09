package com.ferhat.commutetracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single journey between two [Place]s. Created automatically by the tracker
 * ([isAuto] = true) or by the manual start/stop flow ([isAuto] = false).
 *
 * Endpoints are nullable: an auto trip may start before its origin is resolved, or
 * end somewhere that isn't a known place yet. Such trips simply don't group.
 */
@Entity(
    tableName = "trips",
    foreignKeys = [
        ForeignKey(
            entity = Place::class,
            parentColumns = ["id"],
            childColumns = ["originPlaceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = Place::class,
            parentColumns = ["id"],
            childColumns = ["destinationPlaceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("originPlaceId"),
        Index("destinationPlaceId"),
        Index("startEpochMillis"),
        Index("endEpochMillis"),
    ],
)
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originPlaceId: Long? = null,
    val destinationPlaceId: Long? = null,
    val startEpochMillis: Long,
    val endEpochMillis: Long? = null,
    val distanceMeters: Double = 0.0,
    val sampleCount: Int = 0,
    val isAuto: Boolean = true,
    val isConfirmed: Boolean = false,
    val note: String? = null,
) {
    val isActive: Boolean get() = endEpochMillis == null

    fun durationMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        (endEpochMillis ?: nowMillis) - startEpochMillis
}
