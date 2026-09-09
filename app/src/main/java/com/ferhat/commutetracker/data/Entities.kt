package com.ferhat.commutetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "commutes")
data class CommuteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originName: String,
    val destinationName: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long? = null,
) {
    val isActive: Boolean get() = endEpochMillis == null

    /** Duration in millis so far, or total if finished. */
    fun durationMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        (endEpochMillis ?: nowMillis) - startEpochMillis
}
