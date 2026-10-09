package com.shahaabapps.watchdog.feature.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.model.ChildLocation
import androidx.compose.ui.text.input.KeyboardType
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
    val protectionVm: com.shahaabapps.watchdog.feature.protection.ProtectionViewModel = hiltViewModel()
    val protection by protectionVm.settings.collectAsStateWithLifecycle()
    val pendingVm: com.shahaabapps.watchdog.feature.protection.PendingAppsViewModel = hiltViewModel()
    val pendingApps by pendingVm.pending.collectAsStateWithLifecycle()
    var showRingSheet by remember { mutableStateOf(false) }
    var showProtectionSheet by remember { mutableStateOf(false) }

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
                onClick = onOpenMap,
            )
            StatTile(
                value = if (state.batteryPercent > 0) "${state.batteryPercent}%" else "—",
                label = "Battery",
                icon = Icons.Filled.BatteryStd,
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

        SectionHeader("Protection")
        ProtectionCard(blocked = protection.uninstallBlocked, onClick = { showProtectionSheet = true })

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
    val pinValid = pin.length == 4 && pin.all { it.isDigit() }

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
            Text("Removal PIN (4 digits)", style = MaterialTheme.typography.titleMedium)
            CupertinoTextField(
                value = pin,
                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                placeholder = "e.g. 1234",
                keyboardType = KeyboardType.NumberPassword,
            )
            if (pin.isNotEmpty() && !pinValid) {
                Text("Enter exactly 4 digits.", color = AppTheme.colors.destructive, style = MaterialTheme.typography.bodySmall)
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
            enabled = !blocked || pinValid,
            onClick = {
                onSave(
                    com.shahaabapps.core.model.ProtectionSettings(
                        uninstallBlocked = blocked,
                        pin = if (blocked) pin else "",
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
private fun UsageChart(totalMinutes: Int, apps: List<AppUsageItem>) {
    CupertinoCard {
        if (apps.isEmpty()) {
            Text(
                "No app usage recorded today yet. The top apps by screen time will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.secondaryLabel,
            )
        } else {
            val maxMinutes = apps.maxOf { it.minutes }.coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                UsageDonut(totalMinutes = totalMinutes, apps = apps)
                apps.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(base64 = item.iconBase64, label = item.label, size = 32.dp)
                        Column(modifier = Modifier.weight(1f).padding(start = Spacing.md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    item.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    formatDuration(item.minutes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.secondaryLabel,
                                    modifier = Modifier.padding(start = Spacing.sm),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(AppTheme.colors.secondaryLabel.copy(alpha = 0.15f)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(item.minutes.toFloat() / maxMinutes)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(AppTheme.colors.accent),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageDonut(totalMinutes: Int, apps: List<AppUsageItem>) {
    if (totalMinutes <= 0) return
    val sumTop = apps.sumOf { it.minutes }
    val other = (totalMinutes - sumTop).coerceAtLeast(0)
    val otherColor = AppTheme.colors.secondaryLabel.copy(alpha = 0.3f)
    val segments = buildList {
        apps.forEachIndexed { i, a -> add(USAGE_PALETTE[i % USAGE_PALETTE.size] to a.minutes) }
        if (other > 0) add(otherColor to other)
    }
    val total = segments.sumOf { it.second }.coerceAtLeast(1)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokePx = 20.dp.toPx()
                val diameter = size.minDimension - strokePx
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                var start = -90f
                segments.forEach { (color, minutes) ->
                    val sweep = 360f * minutes / total
                    drawArc(
                        color = color,
                        startAngle = start,
                        sweepAngle = (sweep - 2f).coerceAtLeast(0f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatDuration(totalMinutes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("today", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.secondaryLabel)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            apps.forEachIndexed { i, a ->
                LegendRow(USAGE_PALETTE[i % USAGE_PALETTE.size], a.label, formatDuration(a.minutes))
            }
            if (other > 0) LegendRow(otherColor, "Other", formatDuration(other))
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = Spacing.sm),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.secondaryLabel)
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
