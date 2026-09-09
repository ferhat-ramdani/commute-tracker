package com.ferhat.commutetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ferhat.commutetracker.data.CommuteEntity
import com.ferhat.commutetracker.data.LocationEntity
import com.ferhat.commutetracker.ui.CommuteViewModel
import com.ferhat.commutetracker.ui.formatDateTime
import com.ferhat.commutetracker.ui.formatDuration
import com.ferhat.commutetracker.ui.formatTime
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(vm: CommuteViewModel, onManageLocations: () -> Unit) {
    val locations by vm.locations.collectAsStateWithLifecycle()
    val commutes by vm.commutes.collectAsStateWithLifecycle()
    val active by vm.activeCommute.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Commute Tracker", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        if (active != null) {
            ActiveCommuteCard(
                commute = active!!,
                onEnd = vm::endActiveCommute,
                onCancel = vm::cancelActiveCommute,
            )
        } else {
            StartCommutePanel(
                locations = locations,
                onStart = vm::startCommute,
            )
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onManageLocations, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Place, contentDescription = null)
            Text("  Manage locations")
        }

        Spacer(Modifier.height(20.dp))
        Text("History", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))

        val finished = commutes.filter { !it.isActive }
        if (finished.isEmpty()) {
            Text(
                "No commutes recorded yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(finished, key = { it.id }) { commute ->
                    CommuteRow(commute = commute, onDelete = { vm.deleteCommute(commute) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ActiveCommuteCard(
    commute: CommuteEntity,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(commute.id) {
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
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Commute in progress", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            RouteText(commute.originName, commute.destinationName)
            Spacer(Modifier.height(4.dp))
            Text(
                "Started ${formatTime(commute.startEpochMillis)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                formatDuration(commute.durationMillis(now), withSeconds = true),
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onEnd, modifier = Modifier.fillMaxWidth()) {
                Text("End commute")
            }
            TextButton(onClick = onCancel) {
                Text("Cancel (don't record)")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartCommutePanel(
    locations: List<LocationEntity>,
    onStart: (origin: String, destination: String) -> Unit,
) {
    var origin by remember(locations) {
        mutableStateOf(locations.getOrNull(0)?.name ?: "")
    }
    var destination by remember(locations) {
        mutableStateOf(locations.getOrNull(1)?.name ?: locations.getOrNull(0)?.name ?: "")
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            LocationDropdown("From", locations, origin) { origin = it }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(onClick = {
                    val tmp = origin; origin = destination; destination = tmp
                }) {
                    Icon(Icons.Default.SwapVert, contentDescription = "Swap")
                }
            }
            LocationDropdown("To", locations, destination) { destination = it }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onStart(origin, destination) },
                enabled = origin.isNotBlank() && destination.isNotBlank() && origin != destination,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start commute")
            }
            if (locations.size < 2) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Add at least two locations to start a commute.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationDropdown(
    label: String,
    locations: List<LocationEntity>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            locations.forEach { location ->
                DropdownMenuItem(
                    text = { Text(location.name) },
                    onClick = {
                        onSelected(location.name)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RouteText(origin: String, destination: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(origin, style = MaterialTheme.typography.titleMedium)
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "to",
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Text(destination, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CommuteRow(commute: CommuteEntity, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            RouteText(commute.originName, commute.destinationName)
            Text(
                "${formatDateTime(commute.startEpochMillis)}  •  ${formatDuration(commute.durationMillis())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}
