package com.ferhat.commutetracker.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val transition = event.geofenceTransition
        val placeIds = event.triggeringGeofences
            ?.mapNotNull { GeofenceManager.placeIdFromRequestId(it.requestId) }
            ?: return
        if (placeIds.isEmpty()) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val controller = TrackingController.get(appContext)
                when (transition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER ->
                        controller.onGeofenceEnter(placeIds.first())
                    Geofence.GEOFENCE_TRANSITION_EXIT ->
                        controller.onGeofenceExit(placeIds.first())
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.ferhat.commutetracker.GEOFENCE"
    }
}
