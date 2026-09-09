package com.ferhat.commutetracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A raw location fix collected by [com.ferhat.commutetracker.tracking.TripRecordingService]
 * while the user is moving. These are working data: pruned a few days after the trip
 * they belong to has been analysed.
 */
@Entity(
    tableName = "location_samples",
    indices = [Index("tripId"), Index("epochMillis")],
)
data class LocationSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long? = null,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float,
    val epochMillis: Long,
)
