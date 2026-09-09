package com.ferhat.commutetracker.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Single source of truth for which runtime permissions background tracking needs. */
object TrackingPermissions {

    /** Requested first, together. */
    val foregroundPermissions: List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Must be requested on its own, after fine location is already granted. */
    val backgroundLocationPermission: String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        } else {
            null
        }

    fun hasForegroundLocation(context: Context): Boolean =
        isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasActivityRecognition(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            isGranted(context, Manifest.permission.ACTIVITY_RECOGNITION)

    fun hasBackgroundLocation(context: Context): Boolean =
        backgroundLocationPermission?.let { isGranted(context, it) } ?: true

    fun hasNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            isGranted(context, Manifest.permission.POST_NOTIFICATIONS)

    /** Everything needed for the tracker to actually run in the background. */
    fun hasAllRequired(context: Context): Boolean =
        hasForegroundLocation(context) &&
            hasActivityRecognition(context) &&
            hasBackgroundLocation(context)

    fun missing(context: Context): List<String> = buildList {
        if (!hasForegroundLocation(context)) add("Location")
        if (!hasActivityRecognition(context)) add("Physical activity")
        if (!hasBackgroundLocation(context)) add("Background location (\"Allow all the time\")")
        if (!hasNotifications(context)) add("Notifications")
    }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
