package com.ferhat.commutetracker.data

import androidx.room.Entity

/**
 * A user- or LLM-supplied name for the route between two places (e.g. "Home → Work",
 * "Friday prayer"). Optional: routes without a label are shown as "Origin → Destination".
 */
@Entity(tableName = "route_labels", primaryKeys = ["originPlaceId", "destinationPlaceId"])
data class RouteLabel(
    val originPlaceId: Long,
    val destinationPlaceId: Long,
    val label: String,
    val category: String? = null,
    val isUserSet: Boolean = false,
)
