package com.ferhat.commutetracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One Wi-Fi access point observed at a [Place], with a [weight] that grows as the AP
 * is seen again on later visits and decays over time. Used in Phase B to tell places
 * a few metres apart from each other. Not populated in Phase A.
 */
@Entity(
    tableName = "place_wifi_signatures",
    primaryKeys = ["placeId", "bssid"],
    foreignKeys = [
        ForeignKey(
            entity = Place::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("placeId")],
)
data class PlaceWifiSignature(
    val placeId: Long,
    val bssid: String,
    val weight: Double,
    val lastSeenAt: Long = System.currentTimeMillis(),
)
