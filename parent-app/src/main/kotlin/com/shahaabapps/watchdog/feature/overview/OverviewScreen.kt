package com.shahaabapps.watchdog.feature.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.shahaabapps.core.model.ChildLocation
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.CupertinoPrimaryButton
import com.shahaabapps.core.ui.components.CupertinoTextButton
import com.shahaabapps.core.ui.components.CupertinoTextField
import com.shahaabapps.core.ui.components.SectionHeader
import com.shahaabapps.core.ui.components.StatTile
import com.shahaabapps.core.ui.components.StatusChip
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

private val LIVE_BLUE = Color(0xFF2F7DF6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    onOpenPlaces: () -> Unit,
    onOpenTravel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val childLocation by viewModel.childLocation.collectAsStateWithLifecycle()
    var showRingSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        ) {
            SectionHeader("Today", modifier = Modifier.weight(1f))
            StatusChip(
                text = if (state.online) "Online" else "Offline",
                color = if (state.online) AppTheme.colors.success else AppTheme.colors.secondaryLabel,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatTile(
                value = formatDuration(state.screenTimeMinutes),
                label = "Screen time",
                icon = Icons.Filled.BarChart,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = state.limitedOrBlocked.toString(),
                label = "Apps limited",
                icon = Icons.Filled.Apps,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatTile(
                value = state.placesCount.toString(),
                label = "Places",
                icon = Icons.Filled.Place,
                modifier = Modifier.weight(1f),
                onClick = onOpenPlaces,
            )
            StatTile(
                value = "View",
                label = "Travel history",
                icon = Icons.Filled.Timeline,
                modifier = Modifier.weight(1f),
                onClick = onOpenTravel,
            )
        }

        SectionHeader("Location")
        LocationCard(
            location = childLocation,
            childName = state.childName,
            onOpenMap = onOpenPlaces,
            onRing = { showRingSheet = true },
            onOpenTravel = onOpenTravel,
        )
    }

    if (showRingSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showRingSheet = false }, sheetState = sheetState) {
            RingEditor(
                onRing = { message ->
                    viewModel.ringDevice(message)
                    showRingSheet = false
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationCard(
    location: ChildLocation?,
    childName: String,
    onOpenMap: () -> Unit,
    onRing: () -> Unit,
    onOpenTravel: () -> Unit,
) {
    CupertinoCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Place, contentDescription = null, tint = LIVE_BLUE)
                Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm)) {
                    Text("Live location", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = location?.let { "Updated ${relativeTime(it.timestampMillis)}" }
                            ?: "Waiting for location…",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.secondaryLabel,
                    )
                }
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = AppTheme.colors.secondaryLabel)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Travel history") },
                            onClick = { menuOpen = false; onOpenTravel() },
                            leadingIcon = { Icon(Icons.Filled.Timeline, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Open map") },
                            onClick = { menuOpen = false; onOpenMap() },
                            leadingIcon = { Icon(Icons.Filled.Map, contentDescription = null) },
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppTheme.colors.separator)
                    .clickable(onClick = onOpenMap),
                contentAlignment = Alignment.Center,
            ) {
                if (location != null) {
                    val pos = LatLng(location.latitude, location.longitude)
                    val cameraState = rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(pos, 15f)
                    }
                    LaunchedEffect(pos) {
                        cameraState.position = CameraPosition.fromLatLngZoom(pos, 15f)
                    }
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraState,
                        googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = false,
                            scrollGesturesEnabled = false,
                            zoomGesturesEnabled = false,
                            compassEnabled = false,
                            mapToolbarEnabled = false,
                        ),
                    ) {
                        Marker(
                            state = rememberMarkerState(position = pos),
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                            title = childName.ifBlank { "Child" },
                        )
                    }
                    Box(modifier = Modifier.matchParentSize().clickable(onClick = onOpenMap))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.LocationOff,
                            contentDescription = null,
                            tint = AppTheme.colors.secondaryLabel,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            "No location yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.secondaryLabel,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CupertinoPrimaryButton(text = "Ring device", onClick = onRing, modifier = Modifier.weight(1f))
                CupertinoTextButton(text = "Open map", onClick = onOpenMap, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RingEditor(onRing: (message: String) -> Unit) {
    var message by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Ring device", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Plays a loud alarm on the child's phone and shows your message, even if the phone is on silent. They can stop it with the volume or power button.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.secondaryLabel,
        )
        CupertinoTextField(
            value = message,
            onValueChange = { message = it },
            placeholder = "Message (e.g. Call me now)",
        )
        CupertinoPrimaryButton(text = "Ring now", onClick = { onRing(message) })
    }
}

private fun relativeTime(millis: Long): String {
    if (millis <= 0) return "just now"
    val diff = System.currentTimeMillis() - millis
    val mins = diff / 60_000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 1440 -> "${mins / 60} h ago"
        else -> "${mins / 1440} d ago"
    }
}

private fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}
