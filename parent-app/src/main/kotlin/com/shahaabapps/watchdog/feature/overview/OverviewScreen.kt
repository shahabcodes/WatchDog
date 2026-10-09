package com.shahaabapps.watchdog.feature.overview

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
import androidx.compose.ui.graphics.Color
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

        SectionHeader("Protection")
        ProtectionCard(blocked = protection.uninstallBlocked, onClick = { showProtectionSheet = true })

        SectionHeader("App usage today")
        UsageChart(state.topApps)
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
                onSave = { blocked, pin ->
                    protectionVm.save(blocked, pin)
                    showProtectionSheet = false
                },
            )
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
    onSave: (blocked: Boolean, pin: String) -> Unit,
) {
    var blocked by remember { mutableStateOf(initial.uninstallBlocked) }
    var pin by remember { mutableStateOf(initial.pin) }
    val pinValid = pin.length == 4 && pin.all { it.isDigit() }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Prevent uninstall", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Stops the child from removing Shield from their phone. Shield becomes a device admin and blocks the uninstall screen. To remove it, a parent enters the PIN below on the child's device.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.secondaryLabel,
        )
        CupertinoCard {
            CupertinoListRow(
                title = "Prevent uninstall",
                subtitle = "Protect Shield on the child's phone",
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
        CupertinoPrimaryButton(
            text = "Save",
            enabled = !blocked || pinValid,
            onClick = { onSave(blocked, if (blocked) pin else "") },
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

@Composable
private fun UsageChart(apps: List<AppUsageItem>) {
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
