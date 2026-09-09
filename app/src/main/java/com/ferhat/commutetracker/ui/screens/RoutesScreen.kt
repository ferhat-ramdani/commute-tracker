package com.ferhat.commutetracker.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.AlertDialog
import com.ferhat.commutetracker.analysis.RouteGroup
import com.ferhat.commutetracker.ui.TrackerViewModel
import com.ferhat.commutetracker.ui.formatDuration
import com.ferhat.commutetracker.ui.formatRelative

@Composable
fun RoutesScreen(vm: TrackerViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<RouteGroup?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Routes", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Trips grouped by where you went from and to.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        if (state.routeGroups.isEmpty()) {
            Text(
                "No grouped routes yet. Once a few trips share the same start and end, they show up here.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(state.routeGroups, key = { it.originPlaceId to it.destinationPlaceId }) { group ->
                    RouteGroupCard(group = group, onRename = { editing = group })
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }

    editing?.let { group ->
        RenameRouteDialog(
            currentLabel = group.label,
            onDismiss = { editing = null },
            onSave = { label ->
                vm.setRouteLabel(group.originPlaceId, group.destinationPlaceId, label)
                editing = null
            },
        )
    }
}

@Composable
private fun RouteGroupCard(group: RouteGroup, onRename: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(group.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onRename) { Text("Rename") }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${group.tripCount} trip${if (group.tripCount == 1) "" else "s"} · " +
                    "typ. ${formatDuration(group.medianDurationMillis)} " +
                    "(p90 ${formatDuration(group.p90DurationMillis)})",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "fastest ${formatDuration(group.shortestDurationMillis)} · " +
                    "slowest ${formatDuration(group.longestDurationMillis)} · " +
                    "last ${formatRelative(group.lastTripEndMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            HourSparkline(group.hourHistogram)
        }
    }
}

@Composable
private fun HourSparkline(histogram: IntArray) {
    val max = (histogram.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.Bottom) {
        histogram.forEachIndexed { hour, count ->
            val fraction = count.toFloat() / max
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height((2 + 24 * fraction).dp),
                ) {
                    drawRect(color = androidx.compose.ui.graphics.Color(0xFF1B5E20))
                }
            }
        }
    }
    Text("midnight · 6am · noon · 6pm", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RenameRouteDialog(
    currentLabel: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(currentLabel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this route") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("e.g. Home → Work, Friday prayer") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
