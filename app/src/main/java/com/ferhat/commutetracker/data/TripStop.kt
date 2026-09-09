package com.ferhat.commutetracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A pause during a trip that did NOT end it — e.g. waiting for a bus. Kept so the trip
 * stays a single journey while the wait is still visible ("waited 14 min near X").
 */
@Entity(
    tableName = "trip_stops",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class TripStop(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val latitude: Double,
    val longitude: Double,
    val arrivalMillis: Long,
    val departureMillis: Long,
    @ColumnInfo(defaultValue = "unknown") val kind: String = KIND_UNKNOWN,
) {
    val durationMillis: Long get() = departureMillis - arrivalMillis

    companion object {
        const val KIND_WAIT = "wait"
        const val KIND_TRANSIT = "transit"
        const val KIND_UNKNOWN = "unknown"
    }
}
