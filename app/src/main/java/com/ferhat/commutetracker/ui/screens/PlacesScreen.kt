package com.ferhat.commutetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ferhat.commutetracker.data.Place
import com.ferhat.commutetracker.ui.TrackerViewModel

@Composable
fun PlacesScreen(vm: TrackerViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Place?>(null) }
    var radiusFor by remember { mutableStateOf<Place?>(null) }
    var mergeFrom by remember { mutableStateOf<Place?>(null) }

    val discovered = state.places.filter { !it.isConfirmed }
    val confirmed = state.places.filter { it.isConfirmed }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Places", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Add a place") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { vm.addPlace(newName); newName = "" },
                enabled = newName.isNotBlank(),
            ) { Text("Add") }
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.fillMaxWidth()) {
            if (discovered.isNotEmpty()) {
                item {
                    Text("Needs a name", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                }
                items(discovered, key = { it.id }) { place ->
                    PlaceRow(
                        place = place,
                        highlight = true,
                        onRename = { renaming = place },
                        onConfirm = { vm.confirmPlace(place) },
                        onRadius = { radiusFor = place },
                        onMerge = { mergeFrom = place },
                        onDelete = { vm.deletePlace(place) },
                    )
                    HorizontalDivider()
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            item {
                Text("Your places", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
            }
            if (confirmed.isEmpty()) {
                item {
                    Text(
                        "No saved places yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(confirmed, key = { it.id }) { place ->
                PlaceRow(
                    place = place,
                    highlight = false,
                    onRename = { renaming = place },
                    onConfirm = null,
                    onRadius = { radiusFor = place },
                    onMerge = { mergeFrom = place },
                    onDelete = { vm.deletePlace(place) },
                )
                HorizontalDivider()
            }
        }
    }

    renaming?.let { place ->
        TextPromptDialog(
            title = "Rename place",
            initial = place.label,
            onDismiss = { renaming = null },
            onSave = { vm.renamePlace(place, it); renaming = null },
        )
    }

    radiusFor?.let { place ->
        RadiusDialog(
            place = place,
            onDismiss = { radiusFor = null },
            onSave = { vm.setPlaceRadius(place, it); radiusFor = null },
        )
    }

    mergeFrom?.let { from ->
        MergeDialog(
            from = from,
            candidates = state.places.filter { it.id != from.id },
            onDismiss = { mergeFrom = null },
            onMerge = { keep -> vm.mergePlaces(keep, from); mergeFrom = null },
        )
    }
}

@Composable
private fun PlaceRow(
    place: Place,
    highlight: Boolean,
    onRename: () -> Unit,
    onConfirm: (() -> Unit)?,
    onRadius: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = if (highlight) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(place.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    buildString {
                        append(if (place.source == Place.SOURCE_AUTO) "discovered" else "manual")
                        append(" · ${place.visitCount} visit${if (place.visitCount == 1) "" else "s"}")
                        append(" · ~${place.radiusMeters.toInt()} m")
                        if (!place.hasCoordinates) append(" · no location yet")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onConfirm != null) {
                TextButton(onClick = onConfirm) { Text("Keep") }
            }
            Column {
                TextButton(onClick = { menuOpen = true }) { Text("···") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRename() })
                    DropdownMenuItem(text = { Text("Radius") }, onClick = { menuOpen = false; onRadius() })
                    DropdownMenuItem(text = { Text("Merge into…") }, onClick = { menuOpen = false; onMerge() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun TextPromptDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RadiusDialog(place: Place, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var radius by remember { mutableFloatStateOf(place.radiusMeters.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Match radius for ${place.label}") },
        text = {
            Column {
                Text("${radius.toInt()} metres", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    valueRange = 15f..300f,
                )
                Text(
                    "A smaller radius separates nearby places better but may miss a stop " +
                        "if GPS is poor.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(radius.toDouble()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MergeDialog(
    from: Place,
    candidates: List<Place>,
    onDismiss: () -> Unit,
    onMerge: (Place) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge \"${from.label}\" into…") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().height(280.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(candidates, key = { it.id }) { candidate ->
                    TextButton(
                        onClick = { onMerge(candidate) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(candidate.label, modifier = Modifier.fillMaxWidth()) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
