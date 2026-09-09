package com.ferhat.commutetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ferhat.commutetracker.data.Place
import com.ferhat.commutetracker.data.Trip
import com.ferhat.commutetracker.ui.TrackerViewModel
import com.ferhat.commutetracker.ui.formatDuration
import com.ferhat.commutetracker.ui.formatTime
import kotlinx.coroutines.delay
import java.util.Calendar

@Composable
fun HomeScreen(vm: TrackerViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
    ) {
        Text("Commute Tracker", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        val active = state.activeTrip
        if (active != null) {
            ActiveTripCard(
                trip = active,
                placesById = state.placesById,
                onEnd = vm::endActiveTrip,
                onCancel = vm::cancelActiveTrip,
            )
        } else {
            StatusCard(
                trackingEnabled = state.trackingEnabled,
                permissionsOk = vm.permissionsSatisfied(),
            )
            Spacer(Modifier.height(12.dp))
            ManualTripCard(places = state.places, onStart = vm::startManualTrip)
        }

        if (state.discoveredPlaces.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Text(
                    "${state.discoveredPlaces.size} new place(s) discovered — open the Places tab to name them.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Today", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))

        val todays = state.trips.filter { isToday(it.startEpochMillis) }
        if (todays.isEmpty()) {
            Text(
                "No trips yet today.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            todays.forEach { trip ->
                TripRow(
                    trip = trip,
                    placesById = state.placesById,
                    onConfirm = { vm.confirmTrip(trip.id) },
                    onDelete = { vm.deleteTrip(trip) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun StatusCard(trackingEnabled: Boolean, permissionsOk: Boolean) {
    val (title, body) = when {
        trackingEnabled && permissionsOk -> "Tracking is on" to
            "The app is asleep until you start moving. Trips appear here and in Routes."
        trackingEnabled && !permissionsOk -> "Tracking needs permissions" to
            "Open Settings to finish granting location and activity access."
        else -> "Automatic tracking is off" to
            "Turn it on in Settings to record trips without pressing start."
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ActiveTripCard(
    trip: Trip,
    placesById: Map<Long, Place>,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(trip.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (trip.isAuto) "Trip in progress" else "Commute in progress",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            RouteText(
                origin = placeLabel(trip.originPlaceId, placesById),
                destination = placeLabel(trip.destinationPlaceId, placesById, unknownLabel = "?"),
            )
            Spacer(Modifier.height(4.dp))
            Text("Started ${formatTime(trip.startEpochMillis)}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Text(
                formatDuration(trip.durationMillis(now), withSeconds = true),
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onEnd, modifier = Modifier.fillMaxWidth()) { Text("End now") }
            TextButton(onClick = onCancel) { Text("Discard") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualTripCard(places: List<Place>, onStart: (Long, Long) -> Unit) {
    if (places.size < 2) {
        Card(Modifier.fillMaxWidth()) {
            Text(
                "Add at least two places (Places tab) to log a trip manually.",
                Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    var origin by remember(places) { mutableStateOf(places[0]) }
    var destination by remember(places) { mutableStateOf(places[1]) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Log a trip manually", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            PlaceDropdown("From", places, origin) { origin = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = {
                    val tmp = origin; origin = destination; destination = tmp
                }) { Icon(Icons.Default.SwapVert, contentDescription = "Swap") }
            }
            PlaceDropdown("To", places, destination) { destination = it }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onStart(origin.id, destination.id) },
                enabled = origin.id != destination.id,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceDropdown(
    label: String,
    places: List<Place>,
    selected: Place,
    onSelected: (Place) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            places.forEach { place ->
                DropdownMenuItem(
                    text = { Text(place.label) },
                    onClick = {
                        onSelected(place)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun RouteText(origin: String, destination: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(origin, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "to",
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Text(destination, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TripRow(
    trip: Trip,
    placesById: Map<Long, Place>,
    onConfirm: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            RouteText(
                origin = placeLabel(trip.originPlaceId, placesById),
                destination = placeLabel(trip.destinationPlaceId, placesById),
            )
            Text(
                "${formatTime(trip.startEpochMillis)} · ${formatDuration(trip.durationMillis())}" +
                    if (!trip.isConfirmed && trip.isAuto) " · unconfirmed" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trip.isAuto && !trip.isConfirmed) {
            FilledTonalButton(onClick = onConfirm) { Text("OK") }
        }
        TextButton(onClick = onDelete) { Text("Delete") }
    }
}

internal fun placeLabel(
    placeId: Long?,
    placesById: Map<Long, Place>,
    unknownLabel: String = "Unknown",
): String = placeId?.let { placesById[it]?.label } ?: unknownLabel

private fun isToday(epochMillis: Long): Boolean {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = epochMillis }
    return now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
}
