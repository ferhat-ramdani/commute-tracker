package com.ferhat.commutetracker.tracking

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

/** A single current-location fix, e.g. to pin "Home" to where you're standing. */
object OneShotLocation {

    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Pair<Double, Double>? {
        if (!TrackingPermissions.hasForegroundLocation(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location = runCatching {
            client.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token,
            ).await()
        }.getOrNull() ?: return null
        return location.latitude to location.longitude
    }
}
