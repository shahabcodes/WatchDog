package com.shahaabapps.watchdog.feature.geofence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pentagon
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import com.shahaabapps.watchdog.util.OpenFreeMap
import com.shahaabapps.watchdog.util.drawGeoCircle
import com.shahaabapps.watchdog.util.drawGeoPolygon
import com.shahaabapps.watchdog.util.drawGeoPolyline
import com.shahaabapps.watchdog.util.drawGeoRoute
import com.shahaabapps.watchdog.util.drawLiveDot
import com.shahaabapps.watchdog.util.drawMapMarker
import com.shahaabapps.watchdog.util.drawVertex
import com.shahaabapps.watchdog.util.rememberOpenFreeMapController
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.shahaabapps.core.model.GeoPoint
import com.shahaabapps.core.model.Geofence
import com.shahaabapps.core.model.GeofenceEvent
import com.shahaabapps.core.model.GeofenceShape
import com.shahaabapps.core.model.GeofenceTransition
import com.shahaabapps.core.model.GeofenceTrigger
import com.shahaabapps.core.ui.components.ConfirmDialog
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.CupertinoListRow
import com.shahaabapps.core.ui.components.CupertinoPrimaryButton
import com.shahaabapps.core.ui.components.CupertinoSwitch
import com.shahaabapps.core.ui.components.CupertinoTextField
import kotlinx.coroutines.launch
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import com.shahaabapps.core.model.TrailPoint
import com.shahaabapps.watchdog.feature.travel.TravelHistoryViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DEFAULT_CENTER = 25.2854 to 51.5310 // Doha fallback (lat, lng)
private enum class DrawMode { PIN, AREA }

/** Quick place categories shown as Google-Maps-style pills on the map. */
private val PLACE_CATEGORIES = listOf(
    "home" to "Home",
    "school" to "School",
    "mosque" to "Mosque",
    "gym" to "Gym",
    "hospital" to "Hospital",
    "food" to "Restaurant",
    "shop" to "Shop",
    "park" to "Park",
    "airport" to "Airport",
    "metro" to "Metro",
    "place" to "Place",
)

private fun categoryName(key: String): String =
    PLACE_CATEGORIES.firstOrNull { it.first == key }?.second ?: ""

private fun categoryLabel(key: String): String =
    "a ${categoryName(key).lowercase().ifBlank { "place" }}"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GeofenceScreen(
    onBack: () -> Unit,
    showBack: Boolean = true,
    viewModel: GeofenceViewModel = hiltViewModel(),
) {
    val geofences by viewModel.geofences.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val childLocation by viewModel.childLocation.collectAsStateWithLifecycle()

    var mode by remember { mutableStateOf(DrawMode.PIN) }
    var pending by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var area by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var showCircleSheet by remember { mutableStateOf(false) }
    var showAreaSheet by remember { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }
    var editingGeofence by remember { mutableStateOf<Geofence?>(null) }
    var selectedIcon by remember { mutableStateOf("home") }
    var showCategorySheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun removePoint(index: Int) {
        if (index !in area.indices) return
        val removed = area[index]
        area = area.filterIndexed { i, _ -> i != index }
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Point removed",
                actionLabel = "Undo",
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) {
                area = area.toMutableList().apply { add(index.coerceAtMost(size), removed) }
            }
        }
    }

    val initialCenter = childLocation?.let { it.latitude to it.longitude }
        ?: geofences.firstOrNull()?.let { it.latitude to it.longitude }
        ?: events.firstOrNull()?.let { it.latitude to it.longitude }
        ?: DEFAULT_CENTER
    val controller = rememberOpenFreeMapController(initialCenter.first, initialCenter.second, 14.0)

    // Fly the camera to the child (or first place) once data arrives after the
    // map is ready.
    var didInitialCenter by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(childLocation, geofences, controller.tick.intValue) {
        if (!didInitialCenter && controller.hasMap()) {
            val target = childLocation?.let { it.latitude to it.longitude }
                ?: geofences.firstOrNull()?.let { it.latitude to it.longitude }
            if (target != null) {
                didInitialCenter = true
                runCatching { controller.animateTo(target.first, target.second, 15.0) }
            }
        }
    }

    val accent = AppTheme.colors.accent
    val density = LocalDensity.current
    val placePx = with(density) { 36.dp.toPx() }
    val childPx = with(density) { 40.dp.toPx() }
    val vertexPx = with(density) { 18.dp.toPx() }
    // Google-style route colours for history playback.
    val routeCore = Color(0xFF4285F4)
    val routeCasing = Color(0xFF1967D2)
    val routeFaded = Color(0xFFB9C4D4)
    val routeCorePx = with(density) { 6.dp.toPx() }
    val routeCasingPx = with(density) { 9.dp.toPx() }
    val iconPainters: Map<String, Painter> =
        PlaceIcons.options.associate { (key, vector) -> key to rememberVectorPainter(vector) }
    fun painterFor(key: String): Painter? = iconPainters[key] ?: iconPainters["place"]

    // ---- History / playback (reuses the travel data for this child) ----
    val travelVm: TravelHistoryViewModel = hiltViewModel()
    val travelState by travelVm.uiState.collectAsStateWithLifecycle()
    var historyMode by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    val trail = remember(travelState.trail) { travelState.trail.sortedBy { it.timestampMillis } }
    val startMs = trail.firstOrNull()?.timestampMillis ?: 0L
    val endMs = trail.lastOrNull()?.timestampMillis ?: 0L
    val rangeMs = (endMs - startMs).coerceAtLeast(0L)
    val progress = remember { Animatable(0f) }
    var playing by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1f) }

    // Reset playback whenever the day/data changes or we leave history.
    LaunchedEffect(historyMode, travelState.date, trail.size) {
        playing = false
        runCatching { progress.snapTo(0f) }
    }
    // Drive playback: animate progress 0→1 over ~28s at 1x (scaled by speed).
    LaunchedEffect(playing, speed) {
        if (playing && rangeMs > 0L) {
            val remaining = (1f - progress.value).coerceAtLeast(0f)
            val durationMs = (28_000f * remaining / speed).toInt().coerceAtLeast(1)
            runCatching { progress.animateTo(1f, tween(durationMs, easing = LinearEasing)) }
            playing = false
        }
    }
    // Center the map on the day's trail once, when entering history.
    var historyCentered by remember { mutableStateOf(false) }
    LaunchedEffect(historyMode) { if (!historyMode) historyCentered = false }
    LaunchedEffect(historyMode, trail.size, controller.tick.intValue) {
        if (historyMode && !historyCentered && trail.isNotEmpty() && controller.hasMap()) {
            historyCentered = true
            val cLat = trail.sumOf { it.latitude } / trail.size
            val cLng = trail.sumOf { it.longitude } / trail.size
            runCatching { controller.moveTo(cLat, cLng, 14.0) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        OpenFreeMap(
            controller = controller,
            modifier = Modifier.fillMaxSize(),
            gesturesEnabled = true,
            onMapClick = { lat, lng ->
                if (!historyMode) {
                    when (mode) {
                        DrawMode.PIN -> { pending = lat to lng; showCircleSheet = true }
                        DrawMode.AREA -> { area = area + (lat to lng) }
                    }
                }
            },
            overlay = {
                // Saved places (faint in history mode, for context).
                val placeAlpha = if (historyMode) 0.5f else 1f
                geofences.forEach { g ->
                    if (g.shape == GeofenceShape.POLYGON && g.points.size >= 3) {
                        drawGeoPolygon(
                            controller,
                            g.points.map { it.latitude to it.longitude },
                            fill = accent.copy(alpha = 0.18f * placeAlpha),
                            stroke = accent.copy(alpha = placeAlpha),
                            strokeWidthPx = 5f,
                        )
                    } else {
                        drawGeoCircle(
                            controller,
                            g.latitude, g.longitude, g.radiusMeters.toDouble(),
                            fill = accent.copy(alpha = 0.15f * placeAlpha),
                            stroke = accent.copy(alpha = placeAlpha),
                            strokeWidthPx = 4f,
                        )
                    }
                    if (!historyMode) {
                        drawMapMarker(controller, g.latitude, g.longitude, accent, placePx, icon = painterFor(g.icon))
                    }
                }

                if (historyMode) {
                    // Full recorded path (faded) + the travelled portion as a blue
                    // Google-Maps-style route + moving dot.
                    val allPts = trail.map { it.latitude to it.longitude }
                    drawGeoRoute(controller, allPts, core = routeFaded, casing = routeFaded.copy(alpha = 0.6f), coreWidthPx = routeCorePx, casingWidthPx = routeCasingPx)
                    val t = startMs + (progress.value * rangeMs).toLong()
                    val travelled = trail.filter { it.timestampMillis <= t }.map { it.latitude to it.longitude }
                    val cur = positionAt(trail, t)
                    val travelledWithCur = if (cur != null) travelled + cur else travelled
                    drawGeoRoute(controller, travelledWithCur, core = routeCore, casing = routeCasing, coreWidthPx = routeCorePx, casingWidthPx = routeCasingPx)
                    travelState.stops.forEach { s ->
                        drawMapMarker(controller, s.stop.latitude, s.stop.longitude, accent.copy(alpha = 0.85f), placePx * 0.75f, icon = painterFor(s.placeIcon ?: "place"))
                    }
                    cur?.let { (la, ln) -> drawLiveDot(controller, la, ln, routeCore, childPx * 0.5f) }
                } else {
                    // In-progress drawing
                    if (mode == DrawMode.PIN) {
                        pending?.let { (la, ln) ->
                            drawMapMarker(controller, la, ln, accent, placePx, icon = painterFor(selectedIcon))
                        }
                    } else {
                        when {
                            area.size >= 3 -> drawGeoPolygon(controller, area, accent.copy(alpha = 0.22f), accent, 5f)
                            area.size == 2 -> drawGeoPolyline(controller, area, accent, 6f)
                        }
                        area.forEach { (la, ln) -> drawVertex(controller, la, ln, accent, vertexPx) }
                    }

                    // Child's live location
                    childLocation?.let { loc ->
                        if (loc.accuracyMeters > 0f) {
                            drawGeoCircle(
                                controller,
                                loc.latitude, loc.longitude, loc.accuracyMeters.toDouble(),
                                fill = accent.copy(alpha = 0.12f),
                                stroke = accent.copy(alpha = 0.4f),
                                strokeWidthPx = 2f,
                            )
                        }
                        drawLiveDot(controller, loc.latitude, loc.longitude, accent, childPx * 0.5f)
                    }
                }
            },
        )

        // Top floating controls: a Google-Maps-style back circle + category pills
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(if (showBack) Modifier.statusBarsPadding() else Modifier)
                .fillMaxWidth()
                .padding(top = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (showBack) {
                    Surface(shape = CircleShape, color = AppTheme.colors.card, shadowElevation = 4.dp) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                CategoryPill(
                    label = "Live",
                    icon = Icons.Filled.MyLocation,
                    selected = !historyMode,
                    onClick = { historyMode = false },
                )
                CategoryPill(
                    label = "History",
                    icon = Icons.Filled.History,
                    selected = historyMode,
                    onClick = { historyMode = true },
                )
                if (!historyMode) {
                    // One compact chip; tap to pick what to add (keeps the map clear).
                    AddPlaceChip(
                        mode = mode,
                        selectedIcon = selectedIcon,
                        onClick = { showCategorySheet = true },
                    )
                }
            }

            if (historyMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    SelectorPill(Icons.Filled.CalendarMonth, dateLabel(travelState.date), { showDatePicker = true }, Modifier.weight(1f))
                    SelectorPill(Icons.Filled.Schedule, minuteLabel(travelState.fromMinute), { showFromPicker = true }, Modifier.weight(1f))
                    SelectorPill(Icons.Filled.Schedule, minuteLabel(travelState.toMinute), { showToPicker = true }, Modifier.weight(1f))
                }
            } else {
                HintPill(
                    text = if (mode == DrawMode.PIN) {
                        "Tap the map to drop ${categoryLabel(selectedIcon)}"
                    } else {
                        "Tap points to outline an area (3+)"
                    },
                )
            }
        }

        // History playback bar (bottom)
        if (historyMode) {
            PlaybackBar(
                progress = progress,
                playing = playing,
                hasTrail = trail.isNotEmpty() && rangeMs > 0L,
                speed = speed,
                timeLabel = {
                    if (trail.isEmpty()) "No path this day"
                    else timeLabel(startMs + (progress.value * rangeMs).toLong())
                },
                onPlayPause = { playing = !playing },
                onScrub = { playing = false; scope.launch { progress.snapTo(it) } },
                onCycleSpeed = { speed = when (speed) { 1f -> 2f; 2f -> 4f; else -> 1f } },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else if (mode == DrawMode.AREA && area.isNotEmpty()) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                color = AppTheme.colors.card,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(Spacing.md)
                    .fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.padding(Spacing.sm),
                ) {
                    Text(
                        "${area.size} point${if (area.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(start = Spacing.sm),
                    )
                    IconButton(onClick = { removePoint(area.lastIndex) }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo last point")
                    }
                    IconButton(onClick = { area = emptyList() }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear all")
                    }
                    ExtendedFloatingActionButton(
                        onClick = { showAreaSheet = true },
                        icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                        text = { Text("Save area") },
                        expanded = area.size >= 3,
                    )
                }
            }
        } else {
            // Places list FAB
            ExtendedFloatingActionButton(
                onClick = { showList = true },
                icon = { Icon(Icons.Filled.Place, contentDescription = null) },
                text = { Text("Places (${geofences.size})") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.lg),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp),
        )
    }

    // "What do you want to add?" sheet (compact picker on the map)
    if (showCategorySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showCategorySheet = false }, sheetState = sheetState) {
            CategorySheet(
                mode = mode,
                selectedIcon = selectedIcon,
                onPickCategory = { key ->
                    mode = DrawMode.PIN; area = emptyList(); selectedIcon = key; showCategorySheet = false
                },
                onPickArea = { mode = DrawMode.AREA; pending = null; showCategorySheet = false },
            )
        }
    }

    // Circle naming sheet
    val center = pending
    if (showCircleSheet && center != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showCircleSheet = false; pending = null }, sheetState = sheetState) {
            CircleEditor(
                initialIcon = selectedIcon,
                initialName = categoryName(selectedIcon),
                onSave = { name, radius, trigger, icon, active ->
                    viewModel.saveGeofence(name, center.first, center.second, radius, trigger, icon, active)
                    showCircleSheet = false; pending = null
                },
            )
        }
    }

    // Area naming sheet
    if (showAreaSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showAreaSheet = false }, sheetState = sheetState) {
            AreaEditor(
                onSave = { name, trigger, icon, active ->
                    viewModel.saveArea(name, area.map { GeoPoint(it.first, it.second) }, trigger, icon, active)
                    showAreaSheet = false; area = emptyList()
                },
            )
        }
    }

    // Places list sheet
    if (showList) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showList = false }, sheetState = sheetState) {
            PlacesList(
                geofences = geofences,
                events = events,
                onDelete = { showList = false; viewModel.deleteGeofence(it) },
                onEdit = { showList = false; editingGeofence = it },
                onFocus = { g ->
                    showList = false
                    runCatching { controller.animateTo(g.latitude, g.longitude, 16.0) }
                },
            )
        }
    }

    // Edit place sheet
    val editing = editingGeofence
    if (editing != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { editingGeofence = null }, sheetState = sheetState) {
            EditGeofenceEditor(
                geofence = editing,
                onSave = { name, radius, icon, trigger, active ->
                    viewModel.updateGeofence(editing, name, radius, icon, trigger, active)
                    editingGeofence = null
                },
            )
        }
    }

    // History date + time-range pickers
    if (showDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = travelState.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let {
                        travelVm.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = dpState) }
    }
    if (showFromPicker) {
        TimeRangePickerDialog(
            initialMinutes = travelState.fromMinute,
            onDismiss = { showFromPicker = false },
            onConfirm = { travelVm.setTimeRange(it, travelState.toMinute); showFromPicker = false },
        )
    }
    if (showToPicker) {
        TimeRangePickerDialog(
            initialMinutes = travelState.toMinute,
            onDismiss = { showToPicker = false },
            onConfirm = { travelVm.setTimeRange(travelState.fromMinute, it); showToPicker = false },
        )
    }
}

/** Compact chip showing the current add-selection; tap to open the full picker. */
@Composable
private fun AddPlaceChip(mode: DrawMode, selectedIcon: String, onClick: () -> Unit) {
    val icon = if (mode == DrawMode.AREA) Icons.Filled.Pentagon else PlaceIcons.forKey(selectedIcon)
    val label = if (mode == DrawMode.AREA) "Draw area" else categoryName(selectedIcon).ifBlank { "Add place" }
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        color = AppTheme.colors.accent,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.padding(start = 6.dp),
            )
            Icon(Icons.Filled.ExpandMore, contentDescription = "Change", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySheet(
    mode: DrawMode,
    selectedIcon: String,
    onPickCategory: (String) -> Unit,
    onPickArea: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("What do you want to add?", style = MaterialTheme.typography.headlineMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            CategoryPill(
                label = "Draw area",
                icon = Icons.Filled.Pentagon,
                selected = mode == DrawMode.AREA,
                onClick = onPickArea,
            )
            PLACE_CATEGORIES.forEach { (key, label) ->
                CategoryPill(
                    label = label,
                    icon = PlaceIcons.forKey(key),
                    selected = mode == DrawMode.PIN && selectedIcon == key,
                    onClick = { onPickCategory(key) },
                )
            }
        }
    }
}

@Composable
private fun CategoryPill(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        color = if (selected) AppTheme.colors.accent else AppTheme.colors.card,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) Color.White else AppTheme.colors.accent,
                modifier = Modifier.size(18.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun HintPill(text: String) {
    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = Color.Black.copy(alpha = 0.55f),
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = 6.dp),
        )
    }
}

private fun triggerFor(enter: Boolean, exit: Boolean): GeofenceTrigger = when {
    enter && exit -> GeofenceTrigger.ENTER_EXIT
    exit -> GeofenceTrigger.EXIT
    else -> GeofenceTrigger.ENTER
}

@Composable
private fun AlertToggles(
    enter: Boolean,
    exit: Boolean,
    onEnter: (Boolean) -> Unit,
    onExit: (Boolean) -> Unit,
) {
    Text("Push alerts", style = MaterialTheme.typography.titleMedium)
    CupertinoCard {
        Column {
            CupertinoListRow(
                title = "Notify on arrival",
                subtitle = "Push when they enter",
                trailing = { CupertinoSwitch(checked = enter, onCheckedChange = onEnter) },
            )
            CupertinoListRow(
                title = "Notify on leaving",
                subtitle = "Push when they exit",
                showDivider = false,
                trailing = { CupertinoSwitch(checked = exit, onCheckedChange = onExit) },
            )
        }
    }
}

@Composable
private fun CircleEditor(
    initialIcon: String = "place",
    initialName: String = "",
    onSave: (name: String, radius: Float, trigger: GeofenceTrigger, icon: String, active: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var radius by remember { mutableStateOf(150f) }
    var icon by remember { mutableStateOf(initialIcon) }
    var notifyEnter by remember { mutableStateOf(true) }
    var notifyExit by remember { mutableStateOf(true) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("New place", style = MaterialTheme.typography.headlineMedium)
        CupertinoTextField(value = name, onValueChange = { name = it }, placeholder = "Name (e.g. School)")
        Text("Icon", style = MaterialTheme.typography.titleMedium)
        IconPicker(selected = icon, onSelect = { icon = it })
        Text("Radius: ${radius.toInt()} m", style = MaterialTheme.typography.bodyMedium)
        Slider(value = radius, onValueChange = { radius = it }, valueRange = 50f..1000f)
        AlertToggles(notifyEnter, notifyExit, { notifyEnter = it }, { notifyExit = it })
        CupertinoPrimaryButton(
            text = "Save place",
            onClick = { onSave(name, radius, triggerFor(notifyEnter, notifyExit), icon, notifyEnter || notifyExit) },
        )
    }
}

@Composable
private fun AreaEditor(onSave: (name: String, trigger: GeofenceTrigger, icon: String, active: Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("place") }
    var notifyEnter by remember { mutableStateOf(true) }
    var notifyExit by remember { mutableStateOf(true) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("New area", style = MaterialTheme.typography.headlineMedium)
        CupertinoTextField(value = name, onValueChange = { name = it }, placeholder = "Name (e.g. Neighborhood)")
        Text("Icon", style = MaterialTheme.typography.titleMedium)
        IconPicker(selected = icon, onSelect = { icon = it })
        AlertToggles(notifyEnter, notifyExit, { notifyEnter = it }, { notifyExit = it })
        CupertinoPrimaryButton(
            text = "Save area",
            onClick = { onSave(name, triggerFor(notifyEnter, notifyExit), icon, notifyEnter || notifyExit) },
        )
    }
}

@Composable
private fun EditGeofenceEditor(
    geofence: Geofence,
    onSave: (name: String, radius: Float, icon: String, trigger: GeofenceTrigger, active: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(geofence.name) }
    var radius by remember { mutableStateOf(geofence.radiusMeters) }
    var icon by remember { mutableStateOf(geofence.icon) }
    var notifyEnter by remember {
        mutableStateOf(geofence.active && geofence.trigger != GeofenceTrigger.EXIT)
    }
    var notifyExit by remember {
        mutableStateOf(geofence.active && geofence.trigger != GeofenceTrigger.ENTER)
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Edit place", style = MaterialTheme.typography.headlineMedium)
        CupertinoTextField(value = name, onValueChange = { name = it }, placeholder = "Name")
        Text("Icon", style = MaterialTheme.typography.titleMedium)
        IconPicker(selected = icon, onSelect = { icon = it })
        if (geofence.shape == GeofenceShape.CIRCLE) {
            Text("Radius: ${radius.toInt()} m", style = MaterialTheme.typography.bodyMedium)
            Slider(value = radius, onValueChange = { radius = it }, valueRange = 50f..1000f)
        } else {
            Text(
                "This is a drawn area — its shape can't be changed here. Delete and redraw to change the shape.",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.secondaryLabel,
            )
        }
        AlertToggles(notifyEnter, notifyExit, { notifyEnter = it }, { notifyExit = it })
        CupertinoPrimaryButton(
            text = "Save changes",
            onClick = {
                onSave(name, radius, icon, triggerFor(notifyEnter, notifyExit), notifyEnter || notifyExit)
            },
        )
    }
}

@Composable
private fun IconPicker(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PlaceIcons.options.forEach { (key, vector) ->
            val isSelected = key == selected
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) AppTheme.colors.accent.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .then(
                        if (isSelected) Modifier.border(
                            1.5.dp,
                            AppTheme.colors.accent,
                            androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        ) else Modifier,
                    )
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    vector,
                    contentDescription = key,
                    tint = if (isSelected) AppTheme.colors.accent else AppTheme.colors.secondaryLabel,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun PlacesList(
    geofences: List<Geofence>,
    events: List<GeofenceEvent>,
    onDelete: (String) -> Unit,
    onEdit: (Geofence) -> Unit,
    onFocus: (Geofence) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Geofence?>(null) }
    pendingDelete?.let { target ->
        ConfirmDialog(
            title = "Delete \"${target.name}\"?",
            message = "This place and its alerts will be removed for this child.",
            confirmText = "Delete",
            onConfirm = { onDelete(target.id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl)) {
        Text("Places & areas", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = Spacing.sm))
        if (geofences.isEmpty()) {
            Text("None yet — tap the map to add one.", color = AppTheme.colors.secondaryLabel)
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(geofences, key = { it.id }) { g ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onFocus(g) }.padding(vertical = 4.dp),
                    ) {
                        Icon(
                            PlaceIcons.forKey(g.icon),
                            contentDescription = null,
                            tint = AppTheme.colors.accent,
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = Spacing.md)) {
                            Text(g.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (g.shape == GeofenceShape.POLYGON) "Area · ${g.points.size} points"
                                else "${g.radiusMeters.toInt()} m radius",
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
                                    text = { Text("Edit") },
                                    onClick = { menuOpen = false; onEdit(g) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Edit, contentDescription = null, tint = AppTheme.colors.accent)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = AppTheme.colors.destructive) },
                                    onClick = { menuOpen = false; pendingDelete = g },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Delete, contentDescription = null, tint = AppTheme.colors.destructive)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (events.isNotEmpty()) {
            Text(
                "Recent activity",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.sm),
            )
            val names = geofences.associate { it.id to it.name }
            LazyColumn(
                modifier = Modifier.heightIn(max = 180.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(events, key = { it.id }) { e ->
                    val entered = e.transition == GeofenceTransition.ENTER
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (entered) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = if (entered) AppTheme.colors.success else AppTheme.colors.warn,
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = Spacing.md)) {
                            Text(
                                "${if (entered) "Arrived at" else "Left"} ${names[e.geofenceId] ?: e.geofenceName.ifBlank { "a place" }}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                relativeTime(e.timestampMillis),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.secondaryLabel,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun relativeTime(millis: Long): String {
    if (millis <= 0) return ""
    val diff = System.currentTimeMillis() - millis
    val mins = diff / 60_000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 1440 -> "${mins / 60} h ago"
        else -> "${mins / 1440} d ago"
    }
}

// ---- History / playback UI ----

@Composable
private fun PlaybackBar(
    progress: Animatable<Float, *>,
    playing: Boolean,
    hasTrail: Boolean,
    speed: Float,
    timeLabel: () -> String,
    onPlayPause: () -> Unit,
    onScrub: (Float) -> Unit,
    onCycleSpeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = AppTheme.colors.card,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = modifier.padding(Spacing.md).fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                IconButton(onClick = onPlayPause, enabled = hasTrail) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = if (hasTrail) AppTheme.colors.accent else AppTheme.colors.secondaryLabel,
                    )
                }
                Text(timeLabel(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = AppTheme.colors.accent.copy(alpha = 0.15f),
                    modifier = Modifier.clickable(onClick = onCycleSpeed),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Icon(Icons.Filled.Speed, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(16.dp))
                        Text("${speed.toInt()}x", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.accent, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            Slider(value = progress.value, onValueChange = onScrub, enabled = hasTrail)
        }
    }
}

@Composable
private fun SelectorPill(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = AppTheme.colors.card,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRangePickerDialog(initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (minutes: Int) -> Unit) {
    val tpState = rememberTimePickerState(initialHour = initialMinutes / 60, initialMinute = initialMinutes % 60, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(tpState.hour * 60 + tpState.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                TimePicker(state = tpState)
            }
        },
    )
}

/** Interpolated position along the trail at playback time [t]. */
private fun positionAt(trail: List<TrailPoint>, t: Long): Pair<Double, Double>? {
    if (trail.isEmpty()) return null
    if (t <= trail.first().timestampMillis) return trail.first().latitude to trail.first().longitude
    if (t >= trail.last().timestampMillis) return trail.last().latitude to trail.last().longitude
    for (i in 0 until trail.size - 1) {
        val a = trail[i]
        val b = trail[i + 1]
        if (t in a.timestampMillis..b.timestampMillis) {
            val span = (b.timestampMillis - a.timestampMillis).coerceAtLeast(1L)
            val f = (t - a.timestampMillis).toDouble() / span
            return (a.latitude + (b.latitude - a.latitude) * f) to (a.longitude + (b.longitude - a.longitude) * f)
        }
    }
    return trail.last().latitude to trail.last().longitude
}

private val playbackTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val playbackDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

private fun timeLabel(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(playbackTimeFormatter)

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
    if (date == LocalDate.now()) "Today" else date.format(playbackDateFormatter)
