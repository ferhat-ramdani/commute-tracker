package com.ferhat.commutetracker

import android.app.Application
import com.ferhat.commutetracker.tracking.Notifications

class CommuteTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }
}
