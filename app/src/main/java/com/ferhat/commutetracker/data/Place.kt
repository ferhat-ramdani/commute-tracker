package com.ferhat.commutetracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A meaningful location the user visits. Either created by hand ([SOURCE_USER]) or
 * discovered automatically from stay points ([SOURCE_AUTO]).
 *
 * [latitude]/[longitude] are null only for hand-created places that have never been
 * located yet (e.g. migrated from the v1 manual list).
 */
@Entity(
    tableName = "places",
    indices = [Index("latitude"), Index("longitude")],
)
data class Place(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Double = DEFAULT_RADIUS_METERS,
    val category: String? = null,
    val isConfirmed: Boolean = false,
    val visitCount: Int = 0,
    val firstSeenAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis(),
    val source: String = SOURCE_AUTO,
    /** [KIND_NORMAL] or [KIND_TRANSIT] (bus stop / station — longer mid-trip wait tolerance). */
    @ColumnInfo(defaultValue = "normal") val kind: String = KIND_NORMAL,
    /** Comma-separated Wi-Fi SSID names seen here, used as a naming hint. Phase B fills this. */
    val wifiSsids: String? = null,
) {
    val hasCoordinates: Boolean get() = latitude != null && longitude != null
    val isTransit: Boolean get() = kind == KIND_TRANSIT

    companion object {
        const val DEFAULT_RADIUS_METERS = 35.0

        /** Radius used for the OS geofence around a place (larger than [radiusMeters]). */
        const val GEOFENCE_RADIUS_METERS = 150.0

        const val SOURCE_USER = "USER"
        const val SOURCE_AUTO = "AUTO"

        const val KIND_NORMAL = "normal"
        const val KIND_TRANSIT = "transit"
    }
}
