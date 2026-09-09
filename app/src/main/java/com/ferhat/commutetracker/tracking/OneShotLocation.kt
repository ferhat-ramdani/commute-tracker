package com.ferhat.commutetracker.tracking

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

data class Fix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val epochMillis: Long,
)

/** A single current-location fix, e.g. to pin "Home" to where you're standing. */
object OneShotLocation {

    @SuppressLint("MissingPermission")
    suspend fun fix(context: Context): Fix? {
        if (!TrackingPermissions.hasForegroundLocation(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location = runCatching {
            client.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token,
            ).await()
        }.getOrNull() ?: return null
        return Fix(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else 0f,
            epochMillis = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
    }

    suspend fun current(context: Context): Pair<Double, Double>? =
        fix(context)?.let { it.latitude to it.longitude }
}
