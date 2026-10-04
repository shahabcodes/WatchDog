package com.shahaabapps.watchdog.feature.geofence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pentagon
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.shahaabapps.core.ui.components.CupertinoPrimaryButton
import com.shahaabapps.core.ui.components.CupertinoSegmentedControl
import com.shahaabapps.core.ui.components.CupertinoTextField
import kotlinx.coroutines.launch
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

private val DEFAULT_CENTER = LatLng(25.2854, 51.5310) // Doha fallback
private enum class DrawMode { PIN, AREA }

@OptIn(ExperimentalMaterial3Api::class)
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
    var pending by remember { mutableStateOf<LatLng?>(null) }
    var area by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var showCircleSheet by remember { mutableStateOf(false) }
    var showAreaSheet by remember { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }

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

    val initialCenter = childLocation?.let { LatLng(it.latitude, it.longitude) }
        ?: geofences.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
        ?: events.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
        ?: DEFAULT_CENTER
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialCenter, 14f)
    }

    // Fly the camera to the child the first time their location arrives (if it
    // wasn't available when the map first composed).
    var centeredOnChild by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(childLocation) {
        val loc = childLocation
        if (loc != null && !centeredOnChild) {
            centeredOnChild = true
            runCatching {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15f),
                )
            }
        }
    }

    val accent = AppTheme.colors.accent

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
            onMapClick = { latLng ->
                when (mode) {
                    DrawMode.PIN -> { pending = latLng; showCircleSheet = true }
                    DrawMode.AREA -> { area = area + latLng }
                }
            },
        ) {
            // Existing geofences
            geofences.forEach { g ->
                if (g.shape == GeofenceShape.POLYGON && g.points.size >= 3) {
                    Polygon(
                        points = g.points.map { LatLng(it.latitude, it.longitude) },
                        fillColor = accent.copy(alpha = 0.18f),
                        strokeColor = accent,
                        strokeWidth = 5f,
                    )
                } else {
                    Circle(
                        center = LatLng(g.latitude, g.longitude),
                        radius = g.radiusMeters.toDouble(),
                        fillColor = accent.copy(alpha = 0.15f),
                        strokeColor = accent,
                        strokeWidth = 4f,
                    )
                }
                PlaceMarker(
                    position = LatLng(g.latitude, g.longitude),
                    title = g.name,
                    accent = accent,
                    icon = PlaceIcons.forKey(g.icon),
                )
            }

            // In-progress drawing
            if (mode == DrawMode.PIN) {
                pending?.let { PlaceMarker(position = it, title = "New place", accent = accent) }
            } else {
                area.forEachIndexed { i, pt -> VertexMarker(key = i, position = pt, accent = accent) }
                when {
                    area.size >= 3 -> Polygon(
                        points = area,
                        fillColor = accent.copy(alpha = 0.22f),
                        strokeColor = accent,
                        strokeWidth = 5f,
                    )
                    area.size == 2 -> Polyline(points = area, color = accent, width = 6f)
                }
            }

            // Child's live location
            childLocation?.let { loc ->
                val pos = LatLng(loc.latitude, loc.longitude)
                if (loc.accuracyMeters > 0f) {
                    Circle(
                        center = pos,
                        radius = loc.accuracyMeters.toDouble(),
                        fillColor = accent.copy(alpha = 0.12f),
                        strokeColor = accent.copy(alpha = 0.4f),
                        strokeWidth = 2f,
                    )
                }
                ChildMarker(position = pos, accent = accent)
            }
        }

        // Top floating controls: back + mode toggle + hint
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(if (showBack) Modifier.statusBarsPadding() else Modifier)
                .padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = AppTheme.colors.card,
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                ) {
                    if (showBack) {
                        FilledTonalIconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                    Text(
                        "Places",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = Spacing.sm).weight(1f),
                    )
                    CupertinoSegmentedControl(
                        options = listOf("Pin", "Area"),
                        selectedIndex = if (mode == DrawMode.PIN) 0 else 1,
                        onSelect = { i ->
                            if (i == 0) { mode = DrawMode.PIN; area = emptyList() }
                            else { mode = DrawMode.AREA; pending = null }
                        },
                        modifier = Modifier.width(168.dp),
                    )
                }
            }
            HintPill(
                text = if (mode == DrawMode.PIN) {
                    "Tap the map to drop a place"
                } else {
                    "Tap points to outline an area (3+)"
                },
            )
        }

        // Area drawing controls (bottom)
        if (mode == DrawMode.AREA && area.isNotEmpty()) {
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

    // Circle naming sheet
    val center = pending
    if (showCircleSheet && center != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showCircleSheet = false; pending = null }, sheetState = sheetState) {
            CircleEditor(
                onSave = { name, radius, trigger, icon ->
                    viewModel.saveGeofence(name, center.latitude, center.longitude, radius, trigger, icon)
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
                onSave = { name, trigger, icon ->
                    viewModel.saveArea(name, area.map { GeoPoint(it.latitude, it.longitude) }, trigger, icon)
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
                onDelete = { viewModel.deleteGeofence(it) },
            )
        }
    }

}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
@GoogleMapComposable
private fun ChildMarker(position: LatLng, accent: Color) {
    MarkerComposable(
        "child",
        accent,
        position,
        state = rememberMarkerState(position = position),
        title = "Child's device",
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White)
                .padding(3.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
@GoogleMapComposable
private fun PlaceMarker(
    position: LatLng,
    title: String,
    accent: Color,
    icon: ImageVector = Icons.Filled.Place,
) {
    MarkerComposable(
        position,
        accent,
        icon,
        state = rememberMarkerState(position = position),
        title = title,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White)
                .padding(3.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
@GoogleMapComposable
private fun VertexMarker(key: Int, position: LatLng, accent: Color) {
    MarkerComposable(
        key, position,
        state = rememberMarkerState(position = position),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, accent, CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(accent),
        )
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

@Composable
private fun CircleEditor(onSave: (name: String, radius: Float, trigger: GeofenceTrigger, icon: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf(150f) }
    var icon by remember { mutableStateOf("place") }
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
        CupertinoPrimaryButton(text = "Save place", onClick = { onSave(name, radius, GeofenceTrigger.ENTER_EXIT, icon) })
    }
}

@Composable
private fun AreaEditor(onSave: (name: String, trigger: GeofenceTrigger, icon: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("place") }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("New area", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Alerts trigger around this area.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.secondaryLabel,
        )
        CupertinoTextField(value = name, onValueChange = { name = it }, placeholder = "Name (e.g. Neighborhood)")
        Text("Icon", style = MaterialTheme.typography.titleMedium)
        IconPicker(selected = icon, onSelect = { icon = it })
        CupertinoPrimaryButton(text = "Save area", onClick = { onSave(name, GeofenceTrigger.ENTER_EXIT, icon) })
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
