package com.ferhat.commutetracker.tracking

import android.content.Context
import com.ferhat.commutetracker.data.PlaceRepository
import com.ferhat.commutetracker.data.TripRepository
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.DetectedActivity

/**
 * The tracker's brain. Broadcast receivers and workers all funnel their events here.
 * Holds no long-lived state itself — everything durable lives in [TrackingPreferences]
 * so a cold-started receiver behaves correctly.
 *
 * State machine: IDLE ⇄ MOVING. A trip is "MOVING" from the first movement signal
 * until [SettleCheckWorker] confirms the user has been still for a few minutes.
 */
class TrackingController private constructor(private val context: Context) {

    private val prefs = TrackingPreferences(context)
    private val placeRepository = PlaceRepository(context)
    private val tripRepository = TripRepository(context)
    private val activityTransitionManager = ActivityTransitionManager(context)
    private val geofenceManager = GeofenceManager(context)

    // ---- lifecycle --------------------------------------------------------

    suspend fun enableTracking() {
        Notifications.ensureChannels(context)
        prefs.setEnabled(true)
        activityTransitionManager.register()
        refreshGeofences()
        NightlyMaintenanceWorker.schedule(context)
        prefs.markEvent()
    }

    suspend fun disableTracking() {
        prefs.setEnabled(false)
        activityTransitionManager.unregister()
        geofenceManager.clear()
        SettleCheckWorker.cancel(context)
        NightlyMaintenanceWorker.cancel(context)
        TripRecordingService.stop(context)
        prefs.endMovement()
    }

    /** Re-arm OS subscriptions after a reboot / app update, if tracking is on. */
    suspend fun restoreAfterBoot() {
        if (!prefs.isEnabled()) return
        Notifications.ensureChannels(context)
        activityTransitionManager.register()
        refreshGeofences()
        NightlyMaintenanceWorker.schedule(context)
    }

    suspend fun refreshGeofences() {
        geofenceManager.sync(placeRepository.getLocatedPlaces())
    }

    // ---- event handlers -------------------------------------------------

    suspend fun onActivityTransition(activityType: Int, transitionType: Int) {
        prefs.markEvent()
        if (!prefs.isEnabled()) return
        val entering = transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
        when (activityType) {
            DetectedActivity.STILL ->
                if (entering) scheduleSettleCheck() else startMovement()

            DetectedActivity.IN_VEHICLE,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.RUNNING,
            DetectedActivity.WALKING ->
                if (entering) startMovement()
        }
    }

    suspend fun onGeofenceEnter(placeId: Long) {
        prefs.markEvent()
        prefs.setCurrentPlaceId(placeId)
        if (prefs.movementStartedAt() != null) scheduleSettleCheck()
    }

    suspend fun onGeofenceExit(placeId: Long) {
        prefs.markEvent()
        if (prefs.currentPlaceId() == placeId) prefs.setCurrentPlaceId(null)
        if (prefs.isEnabled()) startMovement()
    }

    // ---- state transitions --------------------------------------------

    private suspend fun startMovement() {
        if (!prefs.isEnabled()) return
        SettleCheckWorker.cancel(context)
        if (prefs.movementStartedAt() != null) return
        if (!TrackingPermissions.hasAllRequired(context)) return
        prefs.beginMovement(System.currentTimeMillis(), prefs.currentPlaceId())
        TripRecordingService.start(context)
    }

    /** Called by [SettleCheckWorker] once the user has stayed still long enough. */
    suspend fun finishMovement() {
        val startedAt = prefs.movementStartedAt() ?: return
        val originPlaceId = prefs.movementOriginPlaceId()
        TripRecordingService.stop(context)
        prefs.endMovement()
        AnalysisWorker.enqueue(context, movementStartMillis = startedAt, originPlaceId = originPlaceId)
    }

    private fun scheduleSettleCheck() = SettleCheckWorker.enqueue(context)

    companion object {
        @Volatile
        private var instance: TrackingController? = null

        fun get(context: Context): TrackingController =
            instance ?: synchronized(this) {
                instance ?: TrackingController(context.applicationContext).also { instance = it }
            }
    }
}
