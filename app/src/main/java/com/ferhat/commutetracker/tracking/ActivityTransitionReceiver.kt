package com.ferhat.commutetracker.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransitionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ActivityTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION || !ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val controller = TrackingController.get(appContext)
                result.transitionEvents.forEach { event ->
                    controller.onActivityTransition(event.activityType, event.transitionType)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.ferhat.commutetracker.ACTIVITY_TRANSITION"
    }
}
