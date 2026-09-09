package com.ferhat.commutetracker.tracking

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ferhat.commutetracker.MainActivity
import com.ferhat.commutetracker.R
import com.ferhat.commutetracker.data.LocationSample
import com.ferhat.commutetracker.data.TripRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Foreground service (type `location`) that samples GPS while the user is moving.
 * Started by [TrackingController] on a "movement" signal and stopped when they settle.
 * Only alive during real journeys — this is where the tracker spends its battery.
 */
class TripRecordingService : LifecycleService() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var tripRepository: TripRepository

    private var sampleCount = 0
    private var startedAtMillis = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val fallbackNow = System.currentTimeMillis()
            lifecycleScope.launch {
                result.locations.forEach { location ->
                    tripRepository.addSample(
                        LocationSample(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracyMeters = if (location.hasAccuracy()) location.accuracy else 0f,
                            speedMetersPerSecond = if (location.hasSpeed()) location.speed else 0f,
                            epochMillis = location.time.takeIf { it > 0L } ?: fallbackNow,
                        ),
                    )
                    sampleCount++
                }
                notifyProgress()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        tripRepository = TripRepository(applicationContext)
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopRecording()
            return START_NOT_STICKY
        }
        startedAtMillis = System.currentTimeMillis()
        startForegroundCompat()
        startLocationUpdates()
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun startForegroundCompat() {
        val type = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, Notifications.ID_TRIP_RECORDING, buildNotification(), type)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!TrackingPermissions.hasForegroundLocation(this)) {
            stopRecording()
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
            .setMaxUpdateDelayMillis(MAX_BATCH_DELAY_MS)
            .setWaitForAccurateLocation(false)
            .build()
        fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    private fun stopRecording() {
        runCatching { fusedClient.removeLocationUpdates(locationCallback) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifyProgress() {
        val manager = androidx.core.app.NotificationManagerCompat.from(this)
        if (TrackingPermissions.hasNotifications(this)) {
            manager.notify(Notifications.ID_TRIP_RECORDING, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val elapsedMin = TimeUnit.MILLISECONDS.toMinutes(
            System.currentTimeMillis() - startedAtMillis,
        ).coerceAtLeast(0)
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_TRIP_RECORDING)
            .setContentTitle("Recording your trip")
            .setContentText("$elapsedMin min · $sampleCount points")
            .setSmallIcon(R.drawable.ic_stat_trip)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val INTERVAL_MS = 20_000L
        private const val MIN_INTERVAL_MS = 10_000L
        private const val MAX_BATCH_DELAY_MS = 90_000L

        const val ACTION_STOP = "com.ferhat.commutetracker.STOP_RECORDING"

        fun start(context: Context) {
            val intent = Intent(context, TripRecordingService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TripRecordingService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
