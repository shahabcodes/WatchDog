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
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Bedtime
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
    modifier: Modifier = Modifier,
    viewModel: OverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val childLocation by viewModel.childLocation.collectAsStateWithLifecycle()
    val currentPlace by viewModel.currentPlace.collectAsStateWithLifecycle()
    val weekly by viewModel.weekly.collectAsStateWithLifecycle()
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
            .background(AppTheme.colors.groupedBackground)
            .statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "top") {
            HomeTopRow(
                childName = state.childName.ifBlank { "Child" },
                onOpenSwitcher = { showChildSheet = true },
                onOpenAlerts = onOpenAlerts,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
        item(key = "hero") {
            StatusHero(
                location = childLocation,
                place = currentPlace,
                online = state.online,
                batteryPercent = state.batteryPercent,
                deviceModel = state.deviceModel,
                onOpenMap = onOpenMap,
            )
        }
        item(key = "actions") {
            QuickActions(
                onRing = { showRingSheet = true },
                onLocate = onOpenMap,
                onRefresh = { viewModel.refresh() },
                onFocus = { showFocusSheet = true },
            )
        }
        item(key = "screentime") {
            ScreenTimeCard(
                totalMinutes = state.screenTimeMinutes,
                yesterdayMinutes = if (weekly.size >= 2) weekly[weekly.lastIndex - 1].minutes else null,
                weekMinutes = weekly.map { it.minutes },
                apps = state.topApps,
            )
        }
        item(key = "bedtime") { BedtimeBanner(focus = focus) }

        if (pendingApps.isNotEmpty()) {
            item(key = "pending") {
                DashSection("New apps to review") {
                    PendingAppsCard(
                        apps = pendingApps,
                        onAllow = { pendingVm.allow(it) },
                        onKeepBlocked = { pendingVm.keepBlocked(it) },
                    )
                }
            }
        }

        item(key = "screen") {
            DashSection("Screen") {
                com.shahaabapps.watchdog.feature.screenshot.ScreenshotSection()
            }
        }
        item(key = "focus") {
            DashSection("Focus modes") {
                com.shahaabapps.watchdog.feature.focus.FocusCard(settings = focus, onEdit = { showFocusSheet = true })
            }
        }
        item(key = "protection") {
            DashSection("Protection") {
                ProtectionCard(blocked = protection.uninstallBlocked, onClick = { showProtectionSheet = true })
            }
        }
        if (weekly.any { it.minutes > 0 }) {
            item(key = "week") {
                DashSection("This week") {
                    WeeklyTrendChart(weekly)
                }
            }
        }
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

/** Status hero: a map strip with a live online chip, then the status + battery. */
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
                        zoom = 16.0,
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
    val bg = if (primary) Color(0xFF15171C) else AppTheme.colors.card
    val fg = if (primary) Color.White else MaterialTheme.colorScheme.onBackground
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
        Icon(icon, contentDescription = null, tint = fg)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg, modifier = Modifier.padding(top = 8.dp))
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
                        color = MaterialTheme.colorScheme.onBackground,
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
            color = AppTheme.colors.secondaryLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
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

private val USAGE_PALETTE = listOf(
    Color(0xFF0E6B5E),
    Color(0xFFD9622B),
    Color(0xFF6E7FD6),
    Color(0xFFE5A14B),
    Color(0xFF8A919E),
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
                    val barColor = if (day.isToday) Color(0xFF0E6B5E) else Color(0xFF0E6B5E).copy(alpha = 0.22f)
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
                            color = if (day.isToday) Color(0xFF0E6B5E) else AppTheme.colors.secondaryLabel,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

