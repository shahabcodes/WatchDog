package com.shahaabapps.watchdog.feature.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.model.ChildLocation
import com.shahaabapps.core.model.PIN_MAX_LEN
import com.shahaabapps.core.model.PIN_MIN_LEN
import androidx.compose.ui.text.input.KeyboardType
import com.shahaabapps.watchdog.util.drawGeoCircle
import com.shahaabapps.watchdog.util.drawLiveDot
import com.shahaabapps.core.ui.components.AppIcon
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.CupertinoListRow
import com.shahaabapps.core.ui.components.CupertinoPrimaryButton
import com.shahaabapps.core.ui.components.CupertinoSwitch
import com.shahaabapps.core.ui.components.CupertinoTextButton
import com.shahaabapps.core.ui.components.CupertinoTextField
import com.shahaabapps.core.ui.components.SectionHeader
import com.shahaabapps.core.ui.components.StatTile
import com.shahaabapps.core.ui.components.StatusChip
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val childLocation by viewModel.childLocation.collectAsStateWithLifecycle()
    val weekly by viewModel.weekly.collectAsStateWithLifecycle()
    val protectionVm: com.shahaabapps.watchdog.feature.protection.ProtectionViewModel = hiltViewModel()
    val protection by protectionVm.settings.collectAsStateWithLifecycle()
    val pendingVm: com.shahaabapps.watchdog.feature.protection.PendingAppsViewModel = hiltViewModel()
    val pendingApps by pendingVm.pending.collectAsStateWithLifecycle()
    val focusVm: com.shahaabapps.watchdog.feature.focus.FocusViewModel = hiltViewModel()
    val focus by focusVm.settings.collectAsStateWithLifecycle()
    var showRingSheet by remember { mutableStateOf(false) }
    var showProtectionSheet by remember { mutableStateOf(false) }
    var showFocusSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFFFDEEF3), Color(0xFFEFF6F3), AppTheme.colors.groupedBackground),
                ),
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        GreetingHero(
            childName = state.childName,
            online = state.online,
            onOpenMap = onOpenMap,
            modifier = Modifier.padding(top = Spacing.md),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatTile(
                value = formatDuration(state.screenTimeMinutes),
                label = "Screen time",
                icon = Icons.Filled.BarChart,
                tint = Color(0xFF14B8A6),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = state.limitedOrBlocked.toString(),
                label = "Apps limited",
                icon = Icons.Filled.Apps,
                tint = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatTile(
                value = state.placesCount.toString(),
                label = "Places",
                icon = Icons.Filled.Place,
                tint = Color(0xFF3B6FF6),
                modifier = Modifier.weight(1f),
                onClick = onOpenMap,
            )
            StatTile(
                value = if (state.batteryPercent > 0) "${state.batteryPercent}%" else "—",
                label = "Battery",
                icon = Icons.Filled.BatteryStd,
                tint = Color(0xFFE5588E),
                modifier = Modifier.weight(1f),
            )
        }

        SectionHeader("Location")
        LocationCard(
            location = childLocation,
            deviceModel = state.deviceModel,
            onOpenMap = onOpenMap,
            onRing = { showRingSheet = true },
        )

        if (pendingApps.isNotEmpty()) {
            SectionHeader("New apps to review")
            PendingAppsCard(
                apps = pendingApps,
                onAllow = { pendingVm.allow(it) },
                onKeepBlocked = { pendingVm.keepBlocked(it) },
            )
        }

        SectionHeader("Focus modes")
        com.shahaabapps.watchdog.feature.focus.FocusCard(settings = focus, onEdit = { showFocusSheet = true })

        SectionHeader("Protection")
        ProtectionCard(blocked = protection.uninstallBlocked, onClick = { showProtectionSheet = true })

        if (weekly.any { it.minutes > 0 }) {
            SectionHeader("This week")
            WeeklyTrendChart(weekly)
        }

        SectionHeader("App usage today")
        UsageChart(state.screenTimeMinutes, state.topApps)
        Spacer(Modifier.height(Spacing.md))
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

    if (showProtectionSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showProtectionSheet = false }, sheetState = sheetState) {
            ProtectionEditor(
                initial = protection,
                onSave = { settings ->
                    protectionVm.save(settings)
                    showProtectionSheet = false
                },
            )
        }
    }

    if (showFocusSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showFocusSheet = false }, sheetState = sheetState) {
            com.shahaabapps.watchdog.feature.focus.FocusEditor(
                initial = focus,
                onSave = { settings ->
                    focusVm.save(settings)
                    showFocusSheet = false
                },
            )
        }
    }
}

@Composable
private fun PendingAppsCard(
    apps: List<com.shahaabapps.core.model.PendingApp>,
    onAllow: (String) -> Unit,
    onKeepBlocked: (String) -> Unit,
) {
    CupertinoCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            apps.forEach { app ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppTheme.colors.warn.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Apps, contentDescription = null, tint = AppTheme.colors.warn)
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = Spacing.md)) {
                            Text(app.label.ifBlank { app.packageName }, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Just installed · blocked for now",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.secondaryLabel,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        CupertinoPrimaryButton(text = "Allow", onClick = { onAllow(app.packageName) }, modifier = Modifier.weight(1f))
                        CupertinoTextButton(text = "Keep blocked", onClick = { onKeepBlocked(app.packageName) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtectionCard(blocked: Boolean, onClick: () -> Unit) {
    CupertinoCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppTheme.colors.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (blocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = null,
                    tint = AppTheme.colors.accent,
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.md)) {
                Text("Prevent uninstall", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (blocked) "On · a PIN is required to remove Shield" else "Off · tap to protect Shield",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.secondaryLabel,
                )
            }
            StatusChip(
                text = if (blocked) "On" else "Off",
                color = if (blocked) AppTheme.colors.success else AppTheme.colors.secondaryLabel,
            )
        }
    }
}

@Composable
private fun ProtectionEditor(
    initial: com.shahaabapps.core.model.ProtectionSettings,
    onSave: (com.shahaabapps.core.model.ProtectionSettings) -> Unit,
) {
    var blocked by remember { mutableStateOf(initial.uninstallBlocked) }
    var pin by remember { mutableStateOf(initial.pin) }
    var blockNewApps by remember { mutableStateOf(initial.blockNewApps) }
    // An existing, still-valid PIN can be kept; otherwise (none yet, or an old
    // too-short PIN) we start in "reset" mode so a compliant one must be entered.
    val existingValid = initial.pin.length in PIN_MIN_LEN..PIN_MAX_LEN && initial.pin.all { it.isDigit() }
    var resetting by remember { mutableStateOf(!existingValid) }
    val pinValid = pin.length in PIN_MIN_LEN..PIN_MAX_LEN && pin.all { it.isDigit() }
    // Save is allowed if protection is off, or a valid PIN is already kept, or a
    // valid new PIN has been entered.
    val canSave = !blocked || (!resetting && existingValid) || (resetting && pinValid)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Protection", style = MaterialTheme.typography.headlineMedium)
        CupertinoCard {
            CupertinoListRow(
                title = "Prevent uninstall",
                subtitle = "Shield can't be removed without the PIN",
                showDivider = false,
                trailing = { CupertinoSwitch(checked = blocked, onCheckedChange = { blocked = it }) },
            )
        }
        if (blocked) {
            if (!resetting) {
                // A PIN is already set — offer to reset it rather than revealing it.
                CupertinoCard {
                    CupertinoListRow(
                        title = "Removal PIN",
                        subtitle = "A PIN is set",
                        showDivider = false,
                        trailing = {
                            Text(
                                text = "Reset PIN",
                                style = MaterialTheme.typography.bodyLarge,
                                color = AppTheme.colors.accent,
                                modifier = Modifier
                                    .clickable { resetting = true; pin = "" }
                                    .padding(horizontal = Spacing.sm, vertical = 4.dp),
                            )
                        },
                    )
                }
            } else {
                Text("New removal PIN ($PIN_MIN_LEN–$PIN_MAX_LEN digits)", style = MaterialTheme.typography.titleMedium)
                CupertinoTextField(
                    value = pin,
                    onValueChange = { if (it.length <= PIN_MAX_LEN && it.all { c -> c.isDigit() }) pin = it },
                    placeholder = "$PIN_MIN_LEN–$PIN_MAX_LEN digits",
                    keyboardType = KeyboardType.NumberPassword,
                )
                if (pin.isNotEmpty() && !pinValid) {
                    Text("Enter $PIN_MIN_LEN to $PIN_MAX_LEN digits.", color = AppTheme.colors.destructive, style = MaterialTheme.typography.bodySmall)
                }
                if (existingValid) {
                    Text(
                        text = "Keep current PIN",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.colors.accent,
                        modifier = Modifier
                            .clickable { resetting = false; pin = initial.pin }
                            .padding(vertical = 4.dp),
                    )
                }
            }
        }
        CupertinoCard {
            CupertinoListRow(
                title = "Block new apps",
                subtitle = "Newly installed apps are blocked until you approve them",
                showDivider = false,
                trailing = { CupertinoSwitch(checked = blockNewApps, onCheckedChange = { blockNewApps = it }) },
            )
        }
        CupertinoPrimaryButton(
            text = "Save",
            enabled = canSave,
            onClick = {
                val finalPin = when {
                    !blocked -> ""
                    resetting -> pin
                    else -> initial.pin
                }
                onSave(
                    com.shahaabapps.core.model.ProtectionSettings(
                        uninstallBlocked = blocked,
                        pin = finalPin,
                        blockNewApps = blockNewApps,
                    ),
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationCard(
    location: ChildLocation?,
    deviceModel: String,
    onOpenMap: () -> Unit,
    onRing: () -> Unit,
) {
    CupertinoCard(onClick = onOpenMap) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppTheme.colors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (location != null) Icons.Filled.Place else Icons.Filled.LocationOff,
                        contentDescription = null,
                        tint = AppTheme.colors.accent,
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = Spacing.md)) {
                    Text("Live location", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = location?.let {
                            buildString {
                                append("Updated ${relativeTime(it.timestampMillis)}")
                                if (deviceModel.isNotBlank()) append(" · $deviceModel")
                            }
                        } ?: "Waiting for location…",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.secondaryLabel,
                    )
                }
                Icon(Icons.Filled.Map, contentDescription = null, tint = AppTheme.colors.secondaryLabel)
            }

            if (location != null) {
                val accent = AppTheme.colors.accent
                val density = androidx.compose.ui.platform.LocalDensity.current
                val dotPx = with(density) { 12.dp.toPx() }
                val miniMap = com.shahaabapps.watchdog.util.rememberOpenFreeMapController(
                    location.latitude, location.longitude, 15.5,
                )
                // Keep the mini-map centred on the child as new fixes arrive.
                androidx.compose.runtime.LaunchedEffect(location.latitude, location.longitude) {
                    runCatching { miniMap.animateTo(location.latitude, location.longitude, 15.5) }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(16.dp)),
                ) {
                    com.shahaabapps.watchdog.util.OpenFreeMap(
                        controller = miniMap,
                        modifier = Modifier.fillMaxSize(),
                        gesturesEnabled = false,
                        onMapClick = { _, _ -> onOpenMap() },
                        overlay = {
                            if (location.accuracyMeters > 0f) {
                                drawGeoCircle(
                                    miniMap,
                                    location.latitude, location.longitude, location.accuracyMeters.toDouble(),
                                    fill = accent.copy(alpha = 0.12f),
                                    stroke = accent.copy(alpha = 0.4f),
                                    strokeWidthPx = 2f,
                                )
                            }
                            drawLiveDot(
                                miniMap, location.latitude, location.longitude, accent, dotPx,
                            )
                        },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CupertinoPrimaryButton(text = "Ring device", onClick = onRing, modifier = Modifier.weight(1f))
                CupertinoTextButton(text = "Open map", onClick = onOpenMap, modifier = Modifier.weight(1f))
            }
        }
    }
}

private val USAGE_PALETTE = listOf(
    Color(0xFF4285F4),
    Color(0xFF34A853),
    Color(0xFFFBBC04),
    Color(0xFFEA4335),
    Color(0xFFA142F4),
)

@Composable
private fun GreetingHero(
    childName: String,
    online: Boolean,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(26.dp),
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier.background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color(0xFFA78BFA), Color(0xFFF0A6CA)),
                ),
            ),
        ) {
            // Soft decorative blob.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp, top = 8.dp)
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f)),
            )
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text("Keeping an eye out 👀", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                Text(
                    childName.ifBlank { "Your child" },
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Box(modifier = Modifier.padding(top = 8.dp)) {
                    StatusChip(
                        text = if (online) "Online" else "Offline",
                        color = if (online) AppTheme.colors.success else AppTheme.colors.secondaryLabel,
                    )
                }
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White,
                    modifier = Modifier.padding(top = Spacing.md).clickable(onClick = onOpenMap),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Filled.Map, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(18.dp))
                        Text("Open map", color = Color(0xFF8B5CF6), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageChart(totalMinutes: Int, apps: List<AppUsageItem>) {
    CupertinoCard {
        if (apps.isEmpty()) {
            Text(
                "No app usage recorded today yet. The top apps by screen time will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.secondaryLabel,
            )
        } else {
            val sumTop = apps.sumOf { it.minutes }
            val other = (totalMinutes - sumTop).coerceAtLeast(0)
            val otherColor = AppTheme.colors.secondaryLabel.copy(alpha = 0.25f)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("Screen time", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.secondaryLabel)
                Text(formatDuration(totalMinutes), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                // Multi-colour stacked usage bar.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(50)),
                ) {
                    apps.forEachIndexed { i, a ->
                        Box(
                            modifier = Modifier
                                .weight(a.minutes.toFloat().coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(USAGE_PALETTE[i % USAGE_PALETTE.size]),
                        )
                    }
                    if (other > 0) {
                        Box(modifier = Modifier.weight(other.toFloat()).fillMaxHeight().background(otherColor))
                    }
                }
                apps.forEachIndexed { i, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(10.dp).clip(RoundedCornerShape(50))
                                .background(USAGE_PALETTE[i % USAGE_PALETTE.size]),
                        )
                        AppIcon(base64 = item.iconBase64, label = item.label, size = 28.dp, modifier = Modifier.padding(start = Spacing.sm))
                        Text(
                            item.label,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = Spacing.sm),
                        )
                        Text(
                            formatDuration(item.minutes),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colors.secondaryLabel,
                        )
                    }
                }
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

private val WEEK_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
private fun WeeklyTrendChart(days: List<DayUsage>) {
    val max = (days.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(1)
    val total = days.sumOf { it.minutes }
    val activeDays = days.count { it.minutes > 0 }.coerceAtLeast(1)
    val average = total / activeDays

    CupertinoCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Daily average", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.secondaryLabel)
                    Text(formatDuration(average), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                }
                Text("${formatDuration(total)} total", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.secondaryLabel)
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(132.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEach { day ->
                    val barColor = if (day.isToday) Color(0xFF7C6FF0) else Color(0xFF7C6FF0).copy(alpha = 0.22f)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        if (day.minutes > 0) {
                            Text(
                                formatDuration(day.minutes),
                                fontSize = 9.sp,
                                color = AppTheme.colors.secondaryLabel,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        // Bar area: fixed height box, bar grows from the bottom.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(fraction = (day.minutes.toFloat() / max).coerceIn(0.03f, 1f))
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                    .background(barColor),
                            )
                        }
                        Text(
                            WEEK_LETTERS[day.date.dayOfWeek.value - 1],
                            fontSize = 11.sp,
                            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (day.isToday) Color(0xFF7C6FF0) else AppTheme.colors.secondaryLabel,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
