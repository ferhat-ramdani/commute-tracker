package com.ferhat.commutetracker.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ferhat.commutetracker.tracking.TrackingPermissions
import com.ferhat.commutetracker.ui.TrackerViewModel

@Composable
fun SettingsScreen(vm: TrackerViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scroll = rememberScrollState()

    // Bumped after returning from a permission prompt so the checks below re-run.
    var permissionTick by remember { mutableIntStateOf(0) }
    val hasForeground = remember(permissionTick) { TrackingPermissions.hasForegroundLocation(context) }
    val hasActivity = remember(permissionTick) { TrackingPermissions.hasActivityRecognition(context) }
    val hasBackground = remember(permissionTick) { TrackingPermissions.hasBackgroundLocation(context) }
    val hasNotifications = remember(permissionTick) { TrackingPermissions.hasNotifications(context) }
    val allReady = hasForeground && hasActivity && hasBackground

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissionTick++ }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissionTick++ }

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatic background tracking", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Detects trips on its own. Sleeps when you're not moving.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.trackingEnabled,
                        onCheckedChange = { checked ->
                            if (checked && !allReady) {
                                permissionTick++ // nudge UI; user must grant below first
                            } else {
                                vm.setTrackingEnabled(checked)
                            }
                        },
                        enabled = allReady || state.trackingEnabled,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Permissions", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        PermissionRow(
            label = "Location (while using the app)",
            granted = hasForeground,
            onGrant = {
                foregroundLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            },
        )
        PermissionRow(
            label = "Physical activity",
            granted = hasActivity,
            onGrant = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    foregroundLauncher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
                }
            },
        )
        PermissionRow(
            label = "Location: \"Allow all the time\"",
            granted = hasBackground,
            enabled = hasForeground,
            onGrant = {
                val perm = TrackingPermissions.backgroundLocationPermission
                if (perm == null) {
                    // pre-Q: background granted with foreground
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Android 11+: only settable from the system settings page.
                    openAppSettings(context)
                } else {
                    backgroundLauncher.launch(perm)
                }
            },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionRow(
                label = "Notifications (trip in progress)",
                granted = hasNotifications,
                onGrant = { foregroundLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) },
            )
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { openBatterySettings(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("Exclude from battery optimisation")
        }
        Text(
            "Recommended so the OS doesn't kill tracking overnight.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text("Privacy", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Every location fix, trip and place stays in this app's private database on " +
                "this device. The app has no internet permission at all — nothing can be " +
                "uploaded, and cloud backup of its data is disabled.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Raw GPS samples are deleted about a week after the trip they belong to has " +
                "been analysed. Trips and places are kept until you delete them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    enabled: Boolean = true,
    onGrant: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (if (granted) "✓  " else "•  ") + label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!granted) {
            OutlinedButton(onClick = onGrant, enabled = enabled) { Text("Grant") }
        }
    }
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun openBatterySettings(context: Context) {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { openAppSettings(context) }
}
