package com.shahaabapps.watchdog.feature.travel

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalDensity
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.Hero
import com.shahaabapps.core.ui.components.HeroIconButton
import com.shahaabapps.core.ui.components.LoadingView
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing
import com.shahaabapps.watchdog.feature.geofence.PlaceIcons
import com.shahaabapps.watchdog.util.OpenFreeMap
import com.shahaabapps.watchdog.util.drawGeoPolyline
import com.shahaabapps.watchdog.util.drawMapMarker
import com.shahaabapps.watchdog.util.rememberOpenFreeMapController
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelHistoryScreen(
    onBack: () -> Unit,
    viewModel: TravelHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accent = AppTheme.colors.accent

    var showDatePicker by remember { mutableStateOf(false) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.groupedBackground),
    ) {
        Hero(
            title = "Travel history",
            subtitle = "Where they went",
            actions = {
                HeroIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            },
        )

        // Date + time-range selectors
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SelectorPill(
                icon = Icons.Filled.CalendarMonth,
                text = dateLabel(state.date),
                onClick = { showDatePicker = true },
                modifier = Modifier.weight(1f),
            )
            SelectorPill(
                icon = Icons.Filled.Schedule,
                text = minuteLabel(state.fromMinute),
                onClick = { showFromPicker = true },
                modifier = Modifier.weight(1f),
            )
            SelectorPill(
                icon = Icons.Filled.Schedule,
                text = minuteLabel(state.toMinute),
                onClick = { showToPicker = true },
                modifier = Modifier.weight(1f),
            )
        }

        val hasData = state.trail.isNotEmpty() || state.stops.isNotEmpty()
        when {
            state.loading -> LoadingView(label = "Loading…", modifier = Modifier.padding(top = Spacing.xl))
            !hasData -> EmptyState()
            else -> Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                TravelMap(
                    trail = state.trail,
                    stops = state.stops,
                    accent = accent,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        bottom = Spacing.lg,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (state.stops.isEmpty()) {
                        item {
                            Text(
                                "Path shown above — no 5+ minute stops in this window.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppTheme.colors.secondaryLabel,
                                modifier = Modifier.padding(vertical = Spacing.sm),
                            )
                        }
                    }
                    items(state.stops, key = { it.stop.id }) { item -> StopRow(item) }
                }
            }
        }
    }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let {
                        viewModel.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = dpState)
        }
    }

    if (showFromPicker) {
        TimePickerDialog(
            initialMinutes = state.fromMinute,
            onDismiss = { showFromPicker = false },
            onConfirm = { viewModel.setTimeRange(it, state.toMinute); showFromPicker = false },
        )
    }
    if (showToPicker) {
        TimePickerDialog(
            initialMinutes = state.toMinute,
            onDismiss = { showToPicker = false },
            onConfirm = { viewModel.setTimeRange(state.fromMinute, it); showToPicker = false },
        )
    }
}

@Composable
private fun SelectorPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AppTheme.colors.card,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(16.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (minutes: Int) -> Unit,
) {
    val tpState = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = false,
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(tpState.hour * 60 + tpState.minute) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                TimePicker(state = tpState)
            }
        },
    )
}

@Composable
private fun TravelMap(
    trail: List<com.shahaabapps.core.model.TrailPoint>,
    stops: List<TravelStopItem>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val allLat = trail.map { it.latitude } + stops.map { it.stop.latitude }
    val allLng = trail.map { it.longitude } + stops.map { it.stop.longitude }
    val centerLat = if (allLat.isEmpty()) 25.2854 else allLat.average()
    val centerLng = if (allLng.isEmpty()) 51.5310 else allLng.average()
    val controller = rememberOpenFreeMapController(centerLat, centerLng, 13.0)
    LaunchedEffect(centerLat, centerLng) {
        controller.moveTo(centerLat, centerLng, 13.0)
    }
    val trailPoints = trail.map { it.latitude to it.longitude }
    val stopPoints = stops.map { it.stop.latitude to it.stop.longitude }
    val markerPx = with(LocalDensity.current) { 20.dp.toPx() }

    CupertinoCard(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            OpenFreeMap(
                controller = controller,
                modifier = Modifier.fillMaxSize(),
                gesturesEnabled = true,
                overlay = {
                    // The actual recorded path (GPS samples), drawn as a solid line.
                    drawGeoPolyline(controller, trailPoints, accent, 9f)
                    stopPoints.forEach { (la, ln) ->
                        drawMapMarker(controller, la, ln, accent, diameterPx = markerPx)
                    }
                },
            )
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
                Icon(
                    PlaceIcons.forKey(item.placeIcon),
                    contentDescription = null,
                    tint = AppTheme.colors.accent,
                    modifier = Modifier.size(16.dp),
                )
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
        Text("No movement recorded", style = MaterialTheme.typography.titleMedium)
        Text(
            "Pick another date or widen the time range. The path appears once Shield has been running on the child's phone.",
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

private fun minuteLabel(min: Int): String {
    val h = min / 60
    val m = min % 60
    val ampm = if (h < 12) "AM" else "PM"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "%d:%02d %s".format(h12, m, ampm)
}

private fun dateLabel(date: LocalDate): String =
    if (date == LocalDate.now()) "Today" else date.format(dateFormatter)
