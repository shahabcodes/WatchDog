package com.shahaabapps.watchdog.feature.geofence

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.shahaabapps.core.model.Geofence
import com.shahaabapps.core.model.GeofenceTrigger
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.CupertinoPrimaryButton
import com.shahaabapps.core.ui.components.CupertinoTextField
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

private val DEFAULT_CENTER = LatLng(25.2854, 51.5310) // Doha; falls back if no data

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceScreen(
    onBack: () -> Unit,
    viewModel: GeofenceViewModel = hiltViewModel(),
) {
    val geofences by viewModel.geofences.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    val initialCenter = geofences.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
        ?: events.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
        ?: DEFAULT_CENTER
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialCenter, 13f)
    }

    var pending by remember { mutableStateOf<LatLng?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.colors.groupedBackground)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.card)
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Places & geofences", style = MaterialTheme.typography.titleMedium)
        }

        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp).weight(1f)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                onMapClick = { pending = it },
            ) {
                geofences.forEach { geofence ->
                    val center = LatLng(geofence.latitude, geofence.longitude)
                    Circle(
                        center = center,
                        radius = geofence.radiusMeters.toDouble(),
                        strokeColor = AppTheme.colors.accent,
                        strokeWidth = 4f,
                        fillColor = AppTheme.colors.accent.copy(alpha = 0.15f),
                    )
                    Marker(state = rememberMarkerState(position = center), title = geofence.name)
                }
                pending?.let { center ->
                    Marker(state = rememberMarkerState(position = center), title = "New place")
                }
            }

            Text(
                text = "Tap the map to drop a new place",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(Spacing.sm)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        }

        if (geofences.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(geofences, key = { it.id }) { geofence ->
                    GeofenceRow(geofence, onDelete = { viewModel.deleteGeofence(geofence.id) })
                }
            }
        }
    }

    val center = pending
    if (center != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { pending = null }, sheetState = sheetState) {
            GeofenceEditor(
                onSave = { name, radius, trigger ->
                    viewModel.saveGeofence(name, center.latitude, center.longitude, radius, trigger)
                    pending = null
                },
            )
        }
    }
}

@Composable
private fun GeofenceRow(geofence: Geofence, onDelete: () -> Unit) {
    CupertinoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Place, contentDescription = null, tint = AppTheme.colors.accent)
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.md)) {
                Text(geofence.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${geofence.radiusMeters.toInt()} m · ${geofence.trigger.name.lowercase().replace('_', '/')}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.secondaryLabel,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = AppTheme.colors.destructive)
            }
        }
    }
}

@Composable
private fun GeofenceEditor(
    onSave: (name: String, radius: Float, trigger: GeofenceTrigger) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf(150f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("New place", style = MaterialTheme.typography.headlineMedium)
        CupertinoTextField(value = name, onValueChange = { name = it }, placeholder = "Name (e.g. School)")
        Text("Radius: ${radius.toInt()} m", style = MaterialTheme.typography.bodyMedium)
        Slider(value = radius, onValueChange = { radius = it }, valueRange = 50f..1000f)
        CupertinoPrimaryButton(
            text = "Save place",
            onClick = { onSave(name, radius, GeofenceTrigger.ENTER_EXIT) },
        )
    }
}
