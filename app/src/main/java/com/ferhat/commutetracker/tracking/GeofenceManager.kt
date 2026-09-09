package com.ferhat.commutetracker.tracking

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ferhat.commutetracker.data.Place
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await

/**
 * Keeps an OS geofence around every located [Place]. Geofences are OS-managed and
 * low-power; they give us a second, independent signal for "left home" / "arrived
 * somewhere" in case an activity transition is missed.
 */
class GeofenceManager(private val context: Context) {

    private val client = LocationServices.getGeofencingClient(context)

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
            .setAction(GeofenceBroadcastReceiver.ACTION)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    @SuppressLint("MissingPermission")
    suspend fun sync(places: List<Place>) {
        if (!TrackingPermissions.hasBackgroundLocation(context) ||
            !TrackingPermissions.hasForegroundLocation(context)
        ) {
            return
        }
        runCatching { client.removeGeofences(pendingIntent()).await() }

        val geofences = places
            .filter { it.hasCoordinates }
            .take(MAX_GEOFENCES)
            .map { place ->
                Geofence.Builder()
                    .setRequestId(requestId(place.id))
                    .setCircularRegion(
                        place.latitude!!,
                        place.longitude!!,
                        Place.GEOFENCE_RADIUS_METERS.toFloat(),
                    )
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(
                        Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT,
                    )
                    .setLoiteringDelay(60_000)
                    .build()
            }
        if (geofences.isEmpty()) return

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()
        runCatching { client.addGeofences(request, pendingIntent()).await() }
    }

    suspend fun clear() {
        runCatching { client.removeGeofences(pendingIntent()).await() }
    }

    companion object {
        private const val REQUEST_CODE = 1002
        private const val MAX_GEOFENCES = 90

        fun requestId(placeId: Long) = "place-$placeId"
        fun placeIdFromRequestId(requestId: String): Long? =
            requestId.removePrefix("place-").toLongOrNull()
    }
}
