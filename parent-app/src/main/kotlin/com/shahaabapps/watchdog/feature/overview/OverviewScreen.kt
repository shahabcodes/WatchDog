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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingDown
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
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
import com.shahaabapps.core.ui.components.AppIcon
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.CupertinoListRow
import com.shahaabapps.core.ui.components.CupertinoPrimaryButton
import com.shahaabapps.core.ui.components.CupertinoSwitch
import com.shahaabapps.core.ui.components.CupertinoTextButton
import com.shahaabapps.core.ui.components.CupertinoTextField
import com.shahaabapps.core.ui.components.StatusChip
import com.shahaabapps.core.ui.theme.AppFonts
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    onOpenMap: () -> Unit,
    onOpenAlerts: () -> Unit = {},
    childNames: List<Pair<String, String>> = emptyList(),
    selectedId: String = "",
    onSelectChild: (String) -> Unit = {},
    onAddChild: () -> Unit = {},
    onManage: () -> Unit = {},
    onSettings: () -> Unit = {},
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: OverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val childLocation by viewModel.childLocation.collectAsStateWithLifecycle()
    val currentPlace by viewModel.currentPlace.collectAsStateWithLifecycle()
    val weekly by viewModel.weekly.collectAsStateWithLifecycle()
    val trackingMode by viewModel.trackingMode.collectAsStateWithLifecycle()
    val protectionVm: com.shahaabapps.watchdog.feature.protection.ProtectionViewModel = hiltViewModel()
    val protection by protectionVm.settings.collectAsStateWithLifecycle()
    val pendingVm: com.shahaabapps.watchdog.feature.protection.PendingAppsViewModel = hiltViewModel()
    val pendingApps by pendingVm.pending.collectAsStateWithLifecycle()
    val focusVm: com.shahaabapps.watchdog.feature.focus.FocusViewModel = hiltViewModel()
    val focus by focusVm.settings.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    var showRingSheet by remember { mutableStateOf(false) }
    var showProtectionSheet by remember { mutableStateOf(false) }
    var showFocusSheet by remember { mutableStateOf(false) }
    var showChildSheet by remember { mutableStateOf(false) }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = modifier.fillMaxSize(),
    ) {
    // LazyColumn so off-screen sections don't all compose on first load.
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.groupedBackground),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Full-bleed hero: the live map runs to the top, behind the name pill
        // and the notifications bell.
        item(key = "hero") {
            HomeHero(
                location = childLocation,
                place = currentPlace,
                online = state.online,
                batteryPercent = state.batteryPercent,
                childName = state.childName.ifBlank { "Child" },
                onOpenSwitcher = { showChildSheet = true },
                onOpenAlerts = onOpenAlerts,
                onOpenMap = onOpenMap,
            )
        }
        // Ring / Locate / Refresh / Focus toggles.
        item(key = "actions") {
            Box(Modifier.padding(horizontal = Spacing.lg)) {
                QuickActions(
                    onRing = { showRingSheet = true },
                    onLocate = onOpenMap,
                    onRefresh = { viewModel.refresh() },
                    onFocus = { showFocusSheet = true },
                )
            }
        }
        // Screen time today.
        item(key = "screentime") {
            Box(Modifier.padding(horizontal = Spacing.lg)) {
                ScreenTimeCard(
                    totalMinutes = state.screenTimeMinutes,
                    yesterdayMinutes = if (weekly.size >= 2) weekly[weekly.lastIndex - 1].minutes else null,
                    weekMinutes = weekly.map { it.minutes },
                    apps = state.topApps,
                )
            }
        }
        // New-apps review (conditional alert) — kept near the top so it's seen.
        if (pendingApps.isNotEmpty()) {
            item(key = "pending") {
                Box(Modifier.padding(horizontal = Spacing.lg)) {
                    DashSection("New apps to review") {
                        PendingAppsCard(
                            apps = pendingApps,
                            onAllow = { pendingVm.allow(it) },
                            onKeepBlocked = { pendingVm.keepBlocked(it) },
                        )
                    }
                }
            }
        }
        // Screen capture.
        item(key = "screen") {
            Box(Modifier.padding(horizontal = Spacing.lg)) {
                DashSection("Screen") {
                    com.shahaabapps.watchdog.feature.screenshot.ScreenshotSection()
                }
            }
        }
        // Focus modes.
        item(key = "focus") {
            Box(Modifier.padding(horizontal = Spacing.lg)) {
                DashSection("Focus modes") {
                    com.shahaabapps.watchdog.feature.focus.FocusCard(settings = focus, onEdit = { showFocusSheet = true })
                }
            }
        }
        // Protection.
        item(key = "protection") {
            Box(Modifier.padding(horizontal = Spacing.lg)) {
                DashSection("Protection") {
                    ProtectionCard(blocked = protection.uninstallBlocked, onClick = { showProtectionSheet = true })
                }
            }
        }
        // This week.
        if (weekly.any { it.minutes > 0 }) {
            item(key = "week") {
                Box(Modifier.padding(horizontal = Spacing.lg)) {
                    DashSection("This week") {
                        WeeklyTrendChart(weekly)
                    }
                }
            }
        }
        // Battery.
        item(key = "battery") {
            Box(Modifier.padding(horizontal = Spacing.lg)) {
                DashSection("Battery") {
                    BatteryCard(
                        percent = state.batteryPercent,
                        charging = state.charging,
                        drainPerHour = state.batteryDrainPerHour,
                        stats = state.batteryStats,
                    )
                }
            }
        }
        // Location tracking mode.
        item(key = "tracking") {
            Box(Modifier.padding(horizontal = Spacing.lg)) {
                DashSection("Location tracking") {
                    TrackingCard(mode = trackingMode, onSelect = { viewModel.setTrackingMode(it) })
                }
            }
        }
        // Permissions on the child's phone.
        state.permissions?.let { perms ->
            item(key = "permissions") {
                Box(Modifier.padding(horizontal = Spacing.lg)) {
                    DashSection("Permissions on ${state.childName}'s phone") {
                        PermissionsCard(perms, onRequest = { viewModel.requestPermission(it) })
                    }
                }
            }
        }
        // Next scheduled downtime banner.
        item(key = "bedtime") { Box(Modifier.padding(horizontal = Spacing.lg)) { BedtimeBanner(focus = focus) } }
    }
    }

    if (showChildSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showChildSheet = false }, sheetState = sheetState) {
            ChildSwitcherSheet(
                childNames = childNames,
                selectedId = selectedId,
                onSelectChild = { showChildSheet = false; onSelectChild(it) },
                onAddChild = { showChildSheet = false; onAddChild() },
                onSettings = { showChildSheet = false; onSettings() },
                onSignOut = { showChildSheet = false; onSignOut() },
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
            val focusApps by focusVm.installedApps.collectAsStateWithLifecycle()
            com.shahaabapps.watchdog.feature.focus.FocusEditor(
                initial = focus,
                installedApps = focusApps,
                onSave = { settings ->
                    focusVm.save(settings)
                    showFocusSheet = false
                },
            )
        }
    }
}

/**
 * Full-bleed hero: the live map fills the top of the screen (behind the status
 * bar) with the name pill and notifications bell overlaid on top, and the
 * place/battery status over a bottom scrim.
 */
@Composable
private fun HomeHero(
    location: ChildLocation?,
    place: String?,
    online: Boolean,
    batteryPercent: Int,
    childName: String,
    onOpenSwitcher: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenMap: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .clickable(onClick = onOpenMap),
    ) {
        if (location != null) {
            com.shahaabapps.watchdog.util.OpenFreeMapSnapshot(
                lat = location.latitude,
                lng = location.longitude,
                zoom = 18.5,
                dotColor = AppTheme.colors.accent,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(AppTheme.colors.separator.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.LocationOff, contentDescription = null, tint = AppTheme.colors.secondaryLabel)
            }
        }
        // Bottom scrim so the white status text stays legible over the map.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(150.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                    ),
                ),
        )
        // Overlaid name pill + notifications bell.
        HomeTopRow(
            childName = childName,
            onOpenSwitcher = onOpenSwitcher,
            onOpenAlerts = onOpenAlerts,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        // Status overlay.
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(8.dp).clip(CircleShape)
                        .background(if (online) AppTheme.colors.success else Color.White.copy(alpha = 0.7f)),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (online) "Online" else "Offline",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                when {
                    place != null -> "At $place"
                    location != null -> "Outside known areas"
                    else -> "Location unknown"
                },
                color = Color.White,
                fontFamily = AppFonts.display,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
            )
            val sub = buildString {
                if (batteryPercent > 0) append("$batteryPercent% battery")
                location?.let {
                    if (isNotEmpty()) append(" · ")
                    append("updated ${relativeTime(it.timestampMillis)}")
                }
            }
            if (sub.isNotBlank()) {
                Text(sub, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HomeTopRow(
    childName: String,
    onOpenSwitcher: () -> Unit,
    onOpenAlerts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        val initial = childName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        // Avatar + name pill; tap to switch child or add another.
        Surface(
            shape = CircleShape,
            color = AppTheme.colors.card,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.separator),
            modifier = Modifier.clip(CircleShape).clickable(onClick = onOpenSwitcher),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(48.dp).padding(start = 6.dp, end = 14.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                        .background(AppTheme.colors.accent.copy(alpha = 0.16f)),
                ) {
                    Text(initial, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.accent)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    childName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Switch child", tint = AppTheme.colors.secondaryLabel)
            }
        }
        Spacer(Modifier.weight(1f))
        // Notifications bell with unread dot.
        Box {
            Surface(
                shape = CircleShape,
                color = AppTheme.colors.card,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.separator),
                modifier = Modifier.size(48.dp).clickable(onClick = onOpenAlerts),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = MaterialTheme.colorScheme.onBackground)
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 11.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(AppTheme.colors.warn)
                    .border(2.dp, AppTheme.colors.card, CircleShape),
            )
        }
    }
}

/** Bottom-sheet child switcher: switch between children or add another. */
@Composable
private fun ChildSwitcherSheet(
    childNames: List<Pair<String, String>>,
    selectedId: String,
    onSelectChild: (String) -> Unit,
    onAddChild: () -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            "Your children",
            fontFamily = AppFonts.display,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        childNames.forEach { (id, name) ->
            val selected = id == selectedId
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (selected) AppTheme.colors.accent.copy(alpha = 0.10f) else AppTheme.colors.card,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (selected) AppTheme.colors.accent else AppTheme.colors.separator,
                ),
                modifier = Modifier.fillMaxWidth().clickable { onSelectChild(id) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(AppTheme.colors.accent.copy(alpha = 0.16f)),
                    ) {
                        Text(
                            name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.accent,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        name.ifBlank { "Child" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(Icons.Filled.Check, contentDescription = "Current", tint = AppTheme.colors.accent)
                    }
                }
            }
        }
        // Add child
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = AppTheme.colors.card,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.separator),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onAddChild),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(AppTheme.colors.accent.copy(alpha = 0.16f)),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = AppTheme.colors.accent)
                }
                Spacer(Modifier.width(12.dp))
                Text("Add child", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        // Settings
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onSettings)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = AppTheme.colors.secondaryLabel)
            Spacer(Modifier.width(12.dp))
            Text("Settings", style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.secondaryLabel)
        }
        // Log out
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onSignOut)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = AppTheme.colors.destructive)
            Spacer(Modifier.width(12.dp))
            Text("Log out", style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.destructive)
        }
    }
}

@Composable
private fun StatusHero(
    location: ChildLocation?,
    place: String?,
    online: Boolean,
    batteryPercent: Int,
    deviceModel: String,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(26.dp),
        color = AppTheme.colors.card,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.separator),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onOpenMap),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                if (location != null) {
                    com.shahaabapps.watchdog.util.OpenFreeMapSnapshot(
                        lat = location.latitude,
                        lng = location.longitude,
                        zoom = 17.5,
                        dotColor = AppTheme.colors.accent,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(AppTheme.colors.separator.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.LocationOff, contentDescription = null, tint = AppTheme.colors.secondaryLabel)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(AppTheme.colors.card)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape)
                            .background(if (online) AppTheme.colors.success else AppTheme.colors.secondaryLabel),
                    )
                    Text(
                        if (online) "Online" else "Offline",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                val headline = when {
                    place != null -> "At $place"
                    location != null -> "Outside known areas"
                    else -> "Location unknown"
                }
                Text(
                    headline,
                    style = MaterialTheme.typography.headlineMedium,
                )
                val sub = buildString {
                    append(if (online) "Online" else "Offline")
                    if (batteryPercent > 0) append(" · $batteryPercent% battery")
                    location?.let {
                        append(" · updated ${relativeTime(it.timestampMillis)}")
                    }
                }
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.secondaryLabel)
                }
            }
        }
    }
}

/** Four big quick-action tiles; Ring is the primary (dark) one. */
@Composable
private fun QuickActions(
    onRing: () -> Unit,
    onLocate: () -> Unit,
    onRefresh: () -> Unit,
    onFocus: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        QuickActionTile("Ring", Icons.Filled.NotificationsActive, primary = true, onClick = onRing, modifier = Modifier.weight(1f))
        QuickActionTile("Locate", Icons.Filled.MyLocation, onClick = onLocate, modifier = Modifier.weight(1f))
        QuickActionTile("Refresh", Icons.Filled.Refresh, onClick = onRefresh, modifier = Modifier.weight(1f))
        QuickActionTile("Focus", Icons.Filled.Bedtime, onClick = onFocus, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val bg = if (primary) AppTheme.colors.accent else AppTheme.colors.card
    val labelColor = if (primary) Color.White else MaterialTheme.colorScheme.onBackground
    val iconTint = if (primary) AppTheme.colors.gold else AppTheme.colors.accent
    Column(
        modifier = modifier
            .height(80.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .then(if (primary) Modifier else Modifier.border(1.dp, AppTheme.colors.separator, RoundedCornerShape(20.dp)))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = labelColor, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Screen-time card: big number, vs-yesterday delta, 7-day sparkline, stacked bar, app rows. */
@Composable
private fun ScreenTimeCard(totalMinutes: Int, yesterdayMinutes: Int?, weekMinutes: List<Int>, apps: List<AppUsageItem>) {
    CupertinoCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Screen time today", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.secondaryLabel)
                    Text(
                        formatDuration(totalMinutes),
                        fontFamily = com.shahaabapps.core.ui.theme.AppFonts.display,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 38.sp,
                        color = AppTheme.colors.accent,
                    )
                    yesterdayMinutes?.let { y ->
                        val diff = totalMinutes - y
                        val label = when {
                            diff == 0 -> "same as yesterday"
                            diff < 0 -> "${formatDuration(-diff)} less than yesterday"
                            else -> "${formatDuration(diff)} more than yesterday"
                        }
                        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = if (diff <= 0) AppTheme.colors.accent else AppTheme.colors.warn)
                    }
                }
                if (weekMinutes.any { it > 0 }) {
                    val max = (weekMinutes.maxOrNull() ?: 1).coerceAtLeast(1)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.height(52.dp)) {
                        weekMinutes.forEachIndexed { i, m ->
                            val isToday = i == weekMinutes.lastIndex
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .fillMaxHeight(fraction = (m.toFloat() / max).coerceIn(0.08f, 1f))
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isToday) AppTheme.colors.accent else AppTheme.colors.separator),
                            )
                        }
                    }
                }
            }
            if (apps.isNotEmpty()) {
                val sumTop = apps.sumOf { it.minutes }
                val other = (totalMinutes - sumTop).coerceAtLeast(0)
                Row(modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50))) {
                    apps.forEachIndexed { i, a ->
                        Box(modifier = Modifier.weight(a.minutes.toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(USAGE_PALETTE[i % USAGE_PALETTE.size]))
                    }
                    if (other > 0) Box(modifier = Modifier.weight(other.toFloat()).fillMaxHeight().background(AppTheme.colors.separator.copy(alpha = 0.5f)))
                }
                apps.forEachIndexed { i, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(USAGE_PALETTE[i % USAGE_PALETTE.size]))
                        AppIcon(base64 = item.iconBase64, label = item.label, size = 28.dp, modifier = Modifier.padding(start = Spacing.sm))
                        Text(item.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = Spacing.sm))
                        Text(formatDuration(item.minutes), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.secondaryLabel)
                    }
                }
            } else {
                Text("No app usage recorded today yet.", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.secondaryLabel)
            }
        }
    }
}

private data class BatteryInsight(val charge: Boolean, val text: String)

/** Plain-English battery insights from the hourly series: charge runs + heaviest drain. */
private fun batteryInsights(
    hourly: List<com.shahaabapps.core.model.BatterySample>,
    clock: java.text.SimpleDateFormat,
): List<BatteryInsight> {
    if (hourly.size < 2) return emptyList()
    fun t(ms: Long) = clock.format(java.util.Date(ms))
    val out = mutableListOf<BatteryInsight>()

    // Charge runs: a non-decreasing stretch with a net rise of at least 10%.
    var i = 0
    while (i < hourly.lastIndex) {
        if (hourly[i + 1].percent > hourly[i].percent) {
            var j = i
            while (j < hourly.lastIndex && hourly[j + 1].percent >= hourly[j].percent) j++
            val from = hourly[i].percent
            val to = hourly[j].percent
            if (to - from >= 10) {
                out += BatteryInsight(true, "On charge ${t(hourly[i].atMillis)}–${t(hourly[j].atMillis)} · $from% → $to%")
            }
            i = j
        } else {
            i++
        }
    }

    // Heaviest discharge between two consecutive readings.
    var maxDrop = 0
    var a: com.shahaabapps.core.model.BatterySample? = null
    var b: com.shahaabapps.core.model.BatterySample? = null
    for (k in 0 until hourly.lastIndex) {
        val d = hourly[k].percent - hourly[k + 1].percent
        if (d > maxDrop) { maxDrop = d; a = hourly[k]; b = hourly[k + 1] }
    }
    if (maxDrop >= 8 && a != null && b != null) {
        out += BatteryInsight(false, "Heaviest use ${t(a.atMillis)}–${t(b.atMillis)} · −$maxDrop%")
    }
    return out
}

/** Child device battery: level, charging/drain, last charge, and hourly history. */
@Composable
private fun BatteryCard(
    percent: Int,
    charging: Boolean,
    drainPerHour: Double,
    stats: com.shahaabapps.core.model.BatteryStats?,
) {
    val low = percent in 1..15 && !charging
    val valueColor = if (low) AppTheme.colors.destructive else AppTheme.colors.accent
    val status = when {
        charging && percent >= 100 -> "Charged"
        charging -> "Charging"
        drainPerHour > 0.5 -> "Draining about ${drainPerHour.roundToInt()}%/hr"
        percent > 0 -> "On battery"
        else -> "No recent reading"
    }
    val clock = remember { java.text.SimpleDateFormat("h a", java.util.Locale.getDefault()) }
    val insights = remember(stats?.hourly) { batteryInsights(stats?.hourly.orEmpty(), clock) }
    CupertinoCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppTheme.colors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (charging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryStd,
                        contentDescription = null,
                        tint = AppTheme.colors.accent,
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = Spacing.md)) {
                    Text(
                        if (percent > 0) "$percent%" else "—",
                        fontFamily = com.shahaabapps.core.ui.theme.AppFonts.display,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 30.sp,
                        color = valueColor,
                    )
                    Text(status, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.secondaryLabel)
                }
            }
            // Last charge session.
            stats?.takeIf { it.lastChargeEndMillis > 0 }?.let { s ->
                val gained = (s.lastChargeToPercent - s.lastChargeFromPercent).coerceAtLeast(0)
                Text(
                    "Last charged ${relativeTime(s.lastChargeEndMillis)} · +$gained% (${s.lastChargeFromPercent}% → ${s.lastChargeToPercent}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.secondaryLabel,
                )
            }
            // Insights: heaviest-use window + charge sessions, from the hourly data.
            if (insights.isNotEmpty()) {
                insights.forEach { ins ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (ins.charge) Icons.Filled.BatteryChargingFull else Icons.Filled.TrendingDown,
                            contentDescription = null,
                            tint = if (ins.charge) AppTheme.colors.success else AppTheme.colors.warn,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            ins.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                }
            }
            // Hourly history chart (battery % over time), with axis + time labels.
            stats?.hourly?.takeIf { it.isNotEmpty() }?.let { hourly ->
                Text(
                    "Battery level over the last ${hourly.size} ${if (hourly.size == 1) "hour" else "hours"}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.accent,
                )
                Row(modifier = Modifier.fillMaxWidth().height(64.dp)) {
                    // Y-axis labels.
                    Column(
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.fillMaxHeight().padding(end = 6.dp),
                    ) {
                        Text("100%", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.secondaryLabel)
                        Text("50%", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.secondaryLabel)
                        Text("0%", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.secondaryLabel)
                    }
                    // Bars.
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        hourly.forEach { sample ->
                            val chargingBar = sample.percent >= 100
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(fraction = (sample.percent / 100f).coerceIn(0.04f, 1f))
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(
                                        when {
                                            sample.percent <= 15 -> AppTheme.colors.destructive
                                            chargingBar -> AppTheme.colors.success
                                            else -> AppTheme.colors.accent
                                        },
                                    ),
                            )
                        }
                    }
                }
                // Time axis: first reading → now.
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        clock.format(java.util.Date(hourly.first().atMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.secondaryLabel,
                    )
                    Text(
                        "now (${hourly.last().percent}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.secondaryLabel,
                    )
                }
                // Readable per-hour list (newest first), with the change vs the hour before.
                HorizontalDivider(color = AppTheme.colors.separator, modifier = Modifier.padding(top = 4.dp))
                hourly.asReversed().forEachIndexed { i, sample ->
                    // chronological "previous" is the next item in the reversed list
                    val prev = hourly.asReversed().getOrNull(i + 1)
                    val delta = prev?.let { sample.percent - it.percent }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(
                            clock.format(java.util.Date(sample.atMillis)),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (delta != null && delta != 0) {
                            Text(
                                if (delta > 0) "+$delta%" else "$delta%",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (delta > 0) AppTheme.colors.success else AppTheme.colors.secondaryLabel,
                                modifier = Modifier.padding(end = Spacing.sm),
                            )
                        }
                        Text(
                            "${sample.percent}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (sample.percent <= 15) AppTheme.colors.destructive else AppTheme.colors.accent,
                        )
                    }
                }
            }
        }
    }
}

/** Parent selects how aggressively the child samples location. */
@Composable
private fun TrackingCard(
    mode: com.shahaabapps.core.model.TrackingMode,
    onSelect: (com.shahaabapps.core.model.TrackingMode) -> Unit,
) {
    val desc = when (mode) {
        com.shahaabapps.core.model.TrackingMode.SAVER ->
            "Lowest battery use. The path is less precise, especially while travelling."
        com.shahaabapps.core.model.TrackingMode.ADAPTIVE ->
            "Recommended. Precise while moving (follows the road), light on battery when still."
        com.shahaabapps.core.model.TrackingMode.HIGH ->
            "Most precise everywhere. Uses the most battery."
    }
    CupertinoCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppTheme.colors.separator.copy(alpha = 0.4f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                TrackSeg("Saver", mode == com.shahaabapps.core.model.TrackingMode.SAVER, Modifier.weight(1f)) {
                    onSelect(com.shahaabapps.core.model.TrackingMode.SAVER)
                }
                TrackSeg("Balanced", mode == com.shahaabapps.core.model.TrackingMode.ADAPTIVE, Modifier.weight(1f)) {
                    onSelect(com.shahaabapps.core.model.TrackingMode.ADAPTIVE)
                }
                TrackSeg("High", mode == com.shahaabapps.core.model.TrackingMode.HIGH, Modifier.weight(1f)) {
                    onSelect(com.shahaabapps.core.model.TrackingMode.HIGH)
                }
            }
            Text(desc, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.secondaryLabel)
        }
    }
}

@Composable
private fun TrackSeg(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AppTheme.colors.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else AppTheme.colors.secondaryLabel,
        )
    }
}

/** What permissions / special access the child has granted, for the parent. */
@Composable
private fun PermissionsCard(
    perms: com.shahaabapps.core.model.DevicePermissions,
    onRequest: (String) -> Unit,
) {
    val locationLabel = when {
        perms.locationAlways -> "Allowed all the time"
        perms.location -> "While in use only"
        else -> "Off"
    }
    CupertinoCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PermRow(Icons.Filled.LocationOn, "Location", perms.locationAlways, locationLabel, "location", onRequest)
            PermRow(Icons.Filled.Accessibility, "Accessibility", perms.accessibility, if (perms.accessibility) "On" else "Off — blocking & screenshots won't work", "accessibility", onRequest)
            PermRow(Icons.Filled.Schedule, "Usage access", perms.usageAccess, if (perms.usageAccess) "On" else "Off — screen time won't be tracked", "usageAccess", onRequest)
            PermRow(Icons.Filled.Notifications, "Notifications", perms.notifications, if (perms.notifications) "On" else "Off", "notifications", onRequest)
            PermRow(Icons.Filled.BatteryStd, "Unrestricted battery", perms.batteryUnrestricted, if (perms.batteryUnrestricted) "On" else "Off — may be killed in the background", "battery", onRequest)
            PermRow(Icons.Filled.Alarm, "Full-screen alarm", perms.fullScreenAlarm, if (perms.fullScreenAlarm) "On" else "Off — Ring device won't show full-screen", "fullScreenAlarm", onRequest)
        }
    }
}

@Composable
private fun PermRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    ok: Boolean,
    detail: String,
    permKey: String,
    onRequest: (String) -> Unit,
) {
    var requested by remember(permKey, ok) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f).padding(start = Spacing.md)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.secondaryLabel)
        }
        if (!ok) {
            Text(
                if (requested) "Requested" else "Request",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (requested) AppTheme.colors.secondaryLabel else AppTheme.colors.accent,
                modifier = Modifier
                    .padding(end = Spacing.sm)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !requested) { requested = true; onRequest(permKey) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = if (ok) "Granted" else "Not granted",
            tint = if (ok) AppTheme.colors.success else AppTheme.colors.warn,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Navy banner showing the next scheduled focus window (bedtime / school). */
@Composable
private fun BedtimeBanner(focus: com.shahaabapps.core.model.FocusSettings) {
    val window = when {
        focus.bedtime.enabled -> "Bedtime" to focus.bedtime
        focus.school.enabled -> "School" to focus.school
        else -> null
    } ?: return
    fun clock(minute: Int): String {
        val h = (minute / 60) % 24
        val m = minute % 60
        val ampm = if (h < 12) "AM" else "PM"
        val h12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
        return "%d:%02d %s".format(h12, m, ampm)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF1C2140))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(Icons.Filled.Bedtime, contentDescription = null, tint = Color(0xFFC9CFF5))
        Column(modifier = Modifier.weight(1f).padding(start = Spacing.md)) {
            Text("${window.first} at ${clock(window.second.startMinute)}", color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text("Apps pause until ${clock(window.second.endMinute)}", color = Color(0xFFC9CFF5), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** A dashboard section: an uppercase header tucked tightly above its card. */
@Composable
private fun DashSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = AppTheme.colors.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(start = Spacing.xs),
        )
        content()
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

// Navy shades + the brand gold + a neutral grey, so per-app bars stay
// distinguishable without introducing a third brand colour.
private val USAGE_PALETTE = listOf(
    Color(0xFF012169), // navy (accent)
    Color(0xFFF5B83D), // gold
    Color(0xFF1E4FB0), // mid navy
    Color(0xFF86A6E8), // light navy
    Color(0xFF8A919E), // neutral grey
)

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
                    val barColor = if (day.isToday) AppTheme.colors.accent else AppTheme.colors.accent.copy(alpha = 0.22f)
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
                            color = if (day.isToday) AppTheme.colors.accent else AppTheme.colors.secondaryLabel,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

