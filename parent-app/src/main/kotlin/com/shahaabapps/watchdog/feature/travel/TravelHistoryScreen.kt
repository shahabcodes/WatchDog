package com.shahaabapps.watchdog.feature.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.shahaabapps.watchdog.util.rememberCircleMarker
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.Hero
import com.shahaabapps.core.ui.components.HeroIconButton
import com.shahaabapps.core.ui.components.LoadingView
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TravelHistoryScreen(
    onBack: () -> Unit,
    viewModel: TravelHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accent = AppTheme.colors.accent

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.groupedBackground),
    ) {
        Hero(
            title = "Travel history",
            subtitle = "Places they stopped for 5+ minutes",
            actions = {
                HeroIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            },
        )

        // Date filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            TravelRange.entries.forEach { range ->
                FilterChip(
                    selected = state.range == range,
                    onClick = { viewModel.setRange(range) },
                    label = { Text(range.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.15f),
                        selectedLabelColor = accent,
                    ),
                )
            }
        }
        // Time-of-day filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(top = Spacing.xs, bottom = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            TravelTime.entries.forEach { t ->
                FilterChip(
                    selected = state.time == t,
                    onClick = { viewModel.setTime(t) },
                    label = { Text(t.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.15f),
                        selectedLabelColor = accent,
                    ),
                )
            }
        }

        when {
            state.loading -> LoadingView(label = "Loading travel history…", modifier = Modifier.padding(top = Spacing.xl))
            state.stops.isEmpty() -> EmptyState()
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item { TravelMap(items = state.stops, accent = accent) }
                items(state.stops, key = { it.stop.id }) { item -> StopRow(item) }
            }
        }
    }
}

@Composable
private fun TravelMap(items: List<TravelStopItem>, accent: Color) {
    // Chronological order (the list is newest-first).
    val chronological = items.reversed()
    val points = chronological.map { LatLng(it.stop.latitude, it.stop.longitude) }
    val center = LatLng(
        items.map { it.stop.latitude }.average(),
        items.map { it.stop.longitude }.average(),
    )
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, 12f)
    }
    LaunchedEffect(center) {
        cameraState.position = CameraPosition.fromLatLngZoom(center, 12f)
    }

    CupertinoCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(14.dp)),
        ) {
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
                val startIcon = rememberCircleMarker(AppTheme.colors.success)
                val stopIcon = rememberCircleMarker(accent)
                if (points.size >= 2) {
                    Polyline(points = points, color = accent, width = 8f)
                }
                chronological.forEachIndexed { i, item ->
                    Marker(
                        state = rememberMarkerState(position = LatLng(item.stop.latitude, item.stop.longitude)),
                        icon = if (i == 0) startIcon else stopIcon,
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                        title = item.placeName ?: timeLabel(item.stop.arrivalMillis),
                    )
                }
            }
        }
    }
}

@Composable
private fun StopRow(item: TravelStopItem) {
    val stop = item.stop
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(end = Spacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(AppTheme.colors.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Place, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(16.dp))
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(28.dp)
                    .background(AppTheme.colors.separator),
            )
        }

        CupertinoCard(modifier = Modifier.weight(1f)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.placeName?.let { "Stayed at $it" } ?: "Stop",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${durationShort(stop.durationMinutes)} · ${timeLabel(stop.arrivalMillis)} – ${timeLabel(stop.departureMillis)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.secondaryLabel,
                )
                Text(
                    text = dateLabel(stop.arrivalMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.secondaryLabel,
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(Icons.Filled.Place, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(44.dp))
        Text("No stops in this range", style = MaterialTheme.typography.titleMedium)
        Text(
            "When Shield is running, places where they stay for 5+ minutes appear here. Try a wider date range.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
    }
}

private fun durationShort(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

private fun timeLabel(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormatter)

private fun dateLabel(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(dateFormatter)
    }
}
