package com.ferhat.commutetracker.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

object Notifications {
    const val CHANNEL_TRIP_RECORDING = "trip_recording"
    const val CHANNEL_DISCOVERY = "place_discovery"

    const val ID_TRIP_RECORDING = 42
    const val ID_NEW_PLACE = 43

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRIP_RECORDING,
                "Trip recording",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shown only while a trip is being recorded."
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DISCOVERY,
                "New places",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "When the app discovers a new place to name."
            },
        )
    }
}
