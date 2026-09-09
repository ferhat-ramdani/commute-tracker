package com.ferhat.commutetracker.tracking

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ferhat.commutetracker.MainActivity
import com.ferhat.commutetracker.R
import com.ferhat.commutetracker.analysis.TripAnalyzer
import com.ferhat.commutetracker.data.AppDatabase
import java.util.concurrent.TimeUnit

/**
 * Fires a few minutes after the user goes "still". If they're still still (no movement
 * signal cancelled this in the meantime), the trip is over.
 */
class SettleCheckWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        TrackingController.get(applicationContext).finishMovement()
        return Result.success()
    }

    companion object {
        private const val NAME = "settle-check"
        private val DELAY = TimeUnit.MINUTES.toMillis(4)

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SettleCheckWorker>()
                .setInitialDelay(DELAY, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}

/** Segments the just-finished movement session into trips and discovers new places. */
class AnalysisWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val originPlaceId = inputData.getLong(KEY_ORIGIN, -1L).takeIf { it > 0L }
        val analyzer = TripAnalyzer(applicationContext)
        val newPlaceIds = runCatching { analyzer.analyzePendingSamples(originPlaceId) }
            .getOrElse { return Result.retry() }

        TrackingController.get(applicationContext).refreshGeofences()
        if (newPlaceIds.isNotEmpty()) notifyNewPlace(applicationContext, newPlaceIds.size)
        return Result.success()
    }

    private fun notifyNewPlace(context: Context, count: Int) {
        if (!TrackingPermissions.hasNotifications(context)) return
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_DESTINATION, "places"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (count == 1) "A new place needs a name" else "$count new places need names"
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_DISCOVERY)
            .setContentTitle("Commute Tracker")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_trip)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(Notifications.ID_NEW_PLACE, notification)
    }

    companion object {
        const val KEY_START = "movement_start_millis"
        const val KEY_ORIGIN = "origin_place_id"

        fun enqueue(context: Context, movementStartMillis: Long, originPlaceId: Long?) {
            val request = OneTimeWorkRequestBuilder<AnalysisWorker>()
                .setInputData(
                    workDataOf(
                        KEY_START to movementStartMillis,
                        KEY_ORIGIN to (originPlaceId ?: -1L),
                    ),
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}

/** Nightly (charging + Wi-Fi + idle): prune stale samples, re-sync geofences. */
class NightlyMaintenanceWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching {
            TripAnalyzer(applicationContext).pruneOldSamples(retentionDays = 7)
            // Keep the places table honest: bump visit counts are already maintained;
            // just make sure geofences match the current place list.
            TrackingController.get(applicationContext).refreshGeofences()
            AppDatabase.get(applicationContext) // touch to trigger any pending checkpoints
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "nightly-maintenance"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NightlyMaintenanceWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresCharging(true)
                        // No network needed — the app has no internet permission at all.
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .setRequiresDeviceIdle(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
