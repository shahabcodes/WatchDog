package com.shahaabapps.watchdog.feature.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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
import com.shahaabapps.core.model.AppRule
import com.shahaabapps.core.model.ChildLocation
import com.shahaabapps.core.model.TimeWindow
import com.shahaabapps.core.ui.components.ConfirmDialog
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.CupertinoDisclosure
import com.shahaabapps.core.ui.components.CupertinoListRow
import com.shahaabapps.core.ui.components.CupertinoPrimaryButton
import com.shahaabapps.core.ui.components.CupertinoScaffold
import com.shahaabapps.core.ui.components.CupertinoSwitch
import com.shahaabapps.core.ui.components.CupertinoTextButton
import com.shahaabapps.core.ui.components.CupertinoTextField
import com.shahaabapps.core.ui.components.IconBadge
import com.shahaabapps.core.ui.components.LoadingView
import com.shahaabapps.core.ui.components.StatusChip
import androidx.compose.ui.graphics.vector.ImageVector
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

private val LIMIT_OPTIONS = listOf(null, 15, 30, 60, 120, 180)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildDetailScreen(
    onOpenGeofences: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenTravel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChildDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val childLocation by viewModel.childLocation.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AppRuleRow?>(null) }
    var showRingSheet by remember { mutableStateOf(false) }

    CupertinoScaffold(title = state.childName.ifBlank { "Child" }, modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                LocationCard(
                    location = childLocation,
                    childName = state.childName,
                    onOpenMap = onOpenGeofences,
                    onRing = { showRingSheet = true },
                    onOpenTravel = onOpenTravel,
                )
            }
            item {
                NavCard(
                    icon = Icons.Filled.Place,
                    tint = Color(0xFF2F7DF6),
                    title = "Places & geofences",
                    subtitle = "Alerts when they enter or leave a place",
                    onClick = onOpenGeofences,
                )
            }
            item {
                NavCard(
                    icon = Icons.Filled.BarChart,
                    tint = Color(0xFFAF52DE),
                    title = "Screen time",
                    subtitle = "See today's app usage",
                    onClick = onOpenUsage,
                )
            }
            item {
                Text(
                    text = "APPS — tap to set a daily limit or block",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.sm),
                )
            }
            if (state.loadingInventory && state.rows.isEmpty()) {
                item { LoadingView(label = "Loading apps…", modifier = Modifier.padding(vertical = Spacing.lg)) }
            } else if (state.rows.isEmpty()) {
                item { InventoryHint() }
            } else {
                items(state.rows, key = { it.app.packageName }) { row ->
                    CupertinoCard(onClick = { editing = row }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.app.label.ifBlank { row.app.packageName },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = ruleSummary(row.rule),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.secondaryLabel,
                                )
                            }
                            if (row.rule?.blocked == true) {
                                StatusChip(text = "Blocked", color = AppTheme.colors.destructive)
                            } else if (row.rule?.dailyLimitMinutes != null) {
                                StatusChip(text = "Limited", color = AppTheme.colors.accent)
                            }
                        }
                    }
                }
            }
        }
    }

    val current = editing
    if (current != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { editing = null },
            sheetState = sheetState,
        ) {
            RuleEditor(
                row = current,
                onSave = { limit, blocked, schedule ->
                    viewModel.saveRule(current.app, limit, blocked, schedule)
                    editing = null
                },
                onRemove = {
                    viewModel.clearRule(current.app.packageName)
                    editing = null
                },
            )
        }
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

private val LIVE_BLUE = Color(0xFF2F7DF6)

@Composable
private fun NavCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    CupertinoCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = icon, tint = tint)
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.md)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.secondaryLabel,
                )
            }
            CupertinoDisclosure()
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
                    // Transparent overlay so a tap opens our full map, not Google Maps.
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
                CupertinoPrimaryButton(
                    text = "Ring device",
                    onClick = onRing,
                    modifier = Modifier.weight(1f),
                )
                CupertinoTextButton(
                    text = "Open map",
                    onClick = onOpenMap,
                    modifier = Modifier.weight(1f),
                )
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RuleEditor(
    row: AppRuleRow,
    onSave: (dailyLimitMinutes: Int?, blocked: Boolean, schedule: List<TimeWindow>) -> Unit,
    onRemove: () -> Unit,
) {
    var blocked by remember { mutableStateOf(row.rule?.blocked ?: false) }
    var limit by remember { mutableStateOf(row.rule?.dailyLimitMinutes) }
    var windows by remember {
        mutableStateOf<List<Pair<Int, Int>>>(
            row.rule?.schedule?.map { it.startMinuteOfDay to it.endMinuteOfDay } ?: emptyList(),
        )
    }
    // (window index, isStart) currently being edited in the time picker.
    var editing by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = row.app.label.ifBlank { row.app.packageName },
            style = MaterialTheme.typography.headlineMedium,
        )

        CupertinoCard {
            CupertinoListRow(
                title = "Block completely",
                subtitle = "Always off-limits",
                showDivider = false,
                trailing = {
                    CupertinoSwitch(checked = blocked, onCheckedChange = { blocked = it })
                },
            )
        }

        if (!blocked) {
            Text("Daily limit", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                LIMIT_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = limit == option,
                        onClick = { limit = option },
                        label = { Text(limitLabel(option)) },
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }

            Text("Allowed hours", style = MaterialTheme.typography.titleMedium)
            Text(
                "The app works only during these times. Add as many windows as you like; leave empty to allow all day.",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.secondaryLabel,
            )
            CupertinoCard {
                Column {
                    windows.forEachIndexed { i, window ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        ) {
                            Text(
                                formatMinutes(window.first),
                                color = AppTheme.colors.accent,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.clickable { editing = i to true }.padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                            Text(" – ", color = AppTheme.colors.secondaryLabel)
                            Text(
                                formatMinutes(window.second),
                                color = AppTheme.colors.accent,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.clickable { editing = i to false }.padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { windows = windows.filterIndexed { idx, _ -> idx != i } }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove", tint = AppTheme.colors.secondaryLabel)
                            }
                        }
                    }
                    CupertinoListRow(
                        title = "Add hours",
                        leadingIcon = Icons.Filled.Add,
                        showDivider = false,
                        onClick = { windows = windows + (8 * 60 to 11 * 60) },
                    )
                }
            }
        }

        CupertinoPrimaryButton(
            text = "Save",
            onClick = {
                val schedule = if (blocked) {
                    emptyList()
                } else {
                    windows
                        .filter { it.first != it.second }
                        .map { TimeWindow(it.first, it.second, setOf(1, 2, 3, 4, 5, 6, 7)) }
                }
                onSave(if (blocked) null else limit, blocked, schedule)
            },
        )
        if (row.rule != null) {
            CupertinoTextButton(text = "Remove rule", onClick = { confirmRemove = true }, destructive = true)
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = "Remove rule?",
            message = "This clears the limit and schedule for ${row.app.label.ifBlank { "this app" }}.",
            confirmText = "Remove",
            onConfirm = { confirmRemove = false; onRemove() },
            onDismiss = { confirmRemove = false },
        )
    }

    val editingWindow = editing
    if (editingWindow != null) {
        val (index, isStart) = editingWindow
        val initial = if (isStart) windows[index].first else windows[index].second
        TimePickerSheet(initialMinutes = initial, onDismiss = { editing = null }) { picked ->
            windows = windows.mapIndexed { idx, w ->
                if (idx != index) w
                else if (isStart) picked to w.second
                else w.first to picked
            }
            editing = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerSheet(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (minutes: Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                TimePicker(state = state)
            }
        },
    )
}

private fun formatMinutes(min: Int): String {
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

@Composable
private fun InventoryHint() {
    CupertinoCard {
        Text(text = "Waiting for app list", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "Open Shield on the child's phone — its apps will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.secondaryLabel,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

private fun limitLabel(minutes: Int?): String = when (minutes) {
    null -> "No limit"
    in 60..Int.MAX_VALUE -> "${minutes / 60}h"
    else -> "${minutes}m"
}

private fun ruleSummary(rule: AppRule?): String = when {
    rule == null -> "No limit"
    rule.blocked -> "Blocked"
    rule.dailyLimitMinutes != null -> "${limitLabel(rule.dailyLimitMinutes)} per day"
    else -> "No limit"
}
