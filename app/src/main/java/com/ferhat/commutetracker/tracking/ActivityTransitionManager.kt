package com.ferhat.commutetracker.tracking

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.tasks.await

/**
 * Subscribes to OS activity-transition events (still / walking / running / cycling /
 * in-vehicle, ENTER and EXIT). This is the primary, near-zero-battery wake source for
 * the tracker — the sensor hub does the work and only pings us when state changes.
 */
class ActivityTransitionManager(private val context: Context) {

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, ActivityTransitionReceiver::class.java)
            .setAction(ActivityTransitionReceiver.ACTION)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    @SuppressLint("MissingPermission")
    suspend fun register(): Boolean {
        if (!TrackingPermissions.hasActivityRecognition(context)) return false
        return runCatching {
            ActivityRecognition.getClient(context)
                .requestActivityTransitionUpdates(ActivityTransitionRequest(TRANSITIONS), pendingIntent())
                .await()
            true
        }.getOrDefault(false)
    }

    suspend fun unregister() {
        runCatching {
            ActivityRecognition.getClient(context)
                .removeActivityTransitionUpdates(pendingIntent())
                .await()
        }
    }

    companion object {
        private const val REQUEST_CODE = 1001

        private val ACTIVITIES = intArrayOf(
            DetectedActivity.STILL,
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.IN_VEHICLE,
        )

        private val TRANSITIONS: List<ActivityTransition> = ACTIVITIES.flatMap { activity ->
            listOf(
                ActivityTransition.Builder()
                    .setActivityType(activity)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build(),
                ActivityTransition.Builder()
                    .setActivityType(activity)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build(),
            )
        }
    }
}
