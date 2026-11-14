package com.shahaabapps.watchdog.feature.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahaabapps.core.ui.theme.AppFonts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.model.AppRule
import com.shahaabapps.core.model.InstalledApp
import com.shahaabapps.core.model.TimeWindow
import com.shahaabapps.core.ui.components.AppIcon
import com.shahaabapps.core.ui.components.ConfirmDialog
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.CupertinoListRow
import com.shahaabapps.core.ui.components.CupertinoPrimaryButton
import com.shahaabapps.core.ui.components.CupertinoSwitch
import com.shahaabapps.core.ui.components.CupertinoTextButton
import com.shahaabapps.core.ui.components.CupertinoTextField
import com.shahaabapps.core.ui.components.Hero
import com.shahaabapps.core.ui.components.HeroIconButton
import com.shahaabapps.core.ui.components.SectionHeader
import com.shahaabapps.core.ui.components.StatusChip
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing
import com.shahaabapps.core.ui.tour.tourTarget
import com.shahaabapps.core.ui.tour.TourLayer
import com.shahaabapps.core.ui.tour.TourScene
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LIMIT_OPTIONS = listOf(null, 15, 30, 60, 120, 180)

/** How often a supervised app is captured: seconds between shots, and its label. */
private val WATCH_OPTIONS = listOf(300 to "Every 5 min", 60 to "1 a minute", 30 to "2 a minute", 15 to "4 a minute")
private const val DEFAULT_WATCH_SECONDS = 60

private enum class Access { ALLOWED, WATCHED, BLOCKED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(
    onBack: () -> Unit,
    embedded: Boolean = false,
    /** Package whose rule sheet to open once (from a blocked-app alert). */
    openApp: String? = null,
    /** Opens the watched-app screenshots, for one app or (null) all of them. */
    onOpenWatched: (packageName: String?) -> Unit = {},
    viewModel: UsageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AppRow?>(null) }
    // Opened once the apps load; saved, so coming back to the tab doesn't reopen it.
    var openedApp by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(openApp, state.loading) {
        if (openApp == null || openApp == openedApp || state.loading) return@LaunchedEffect
        openedApp = openApp
        state.rows.firstOrNull { it.app.packageName == openApp }?.let { editing = it }
    }
    // The walk-through opens the first app's rule sheet for its steps.
    TourScene("activity.rule") { open -> editing = if (open) state.rows.firstOrNull() else null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.groupedBackground)
            .then(if (embedded) Modifier.statusBarsPadding() else Modifier),
    ) {
        if (embedded) {
            Column(modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.sm)) {
                Text(
                    "Apps",
                    fontFamily = AppFonts.display,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.6).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "Screen time today",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                Text(
                    formatDuration(state.totalMinutes),
                    modifier = Modifier.tourTarget("activity.total"),
                    fontFamily = AppFonts.display,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp,
                    color = AppTheme.colors.accent,
                )
            }
        } else {
            Hero(
                title = "Screen time",
                subtitle = "Today",
                actions = {
                    HeroIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
            ) {
                Text(formatDuration(state.totalMinutes), style = MaterialTheme.typography.displayLarge, color = Color.White)
            }
        }

        // Search + sort
        Column(modifier = Modifier.padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CupertinoTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = "Search apps",
                modifier = Modifier.tourTarget("activity.search"),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                AppFilter.entries.forEach { f ->
                    FilterChip(
                        selected = state.filter == f,
                        onClick = { viewModel.setFilter(f) },
                        label = { Text(f.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppTheme.colors.accent,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().tourTarget("activity.sort").horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                AppSort.entries.forEach { sort ->
                    FilterChip(
                        selected = state.sort == sort,
                        onClick = { viewModel.setSort(sort) },
                        label = { Text(sort.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppTheme.colors.accent.copy(alpha = 0.15f),
                            selectedLabelColor = AppTheme.colors.accent,
                        ),
                    )
                }
            }
        }

        when {
            state.loading -> com.shahaabapps.watchdog.ui.PawLoading(label = "Loading apps…", modifier = Modifier.padding(top = Spacing.xl))
            state.rows.isEmpty() -> EmptyApps(hasQuery = state.query.isNotBlank() || state.filter != AppFilter.ALL)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                val maxMinutes = state.rows.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
                if (state.watchedApps > 0) {
                    item(key = "watched") {
                        WatchedCard(apps = state.watchedApps, shotsToday = state.watchShotsToday) { onOpenWatched(null) }
                    }
                }
                items(state.rows, key = { it.app.packageName }) { row ->
                    AppCard(
                        modifier = if (row === state.rows.first()) Modifier.tourTarget("activity.app") else Modifier,
                        row = row,
                        showInstalled = state.sort == AppSort.INSTALLED,
                        fraction = (row.minutes.toFloat() / maxMinutes).coerceIn(0f, 1f),
                        onClick = { editing = row },
                    )
                }
            }
        }
    }

    val current = editing
    if (current != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { editing = null }, sheetState = sheetState) {
            TourLayer()
            RuleEditor(
                app = current.app,
                rule = current.rule,
                newApp = current.reviewing,
                newAppBlocked = current.reviewing && current.newAppsBlocked,
                onSave = { limit, blocked, schedule, watchEvery, scheduleBlocks ->
                    viewModel.saveRule(
                        current.app, limit, blocked, schedule, watchEvery,
                        scheduleBlocks = scheduleBlocks, clearReview = current.newApp,
                    )
                    editing = null
                },
                onViewShots = {
                    editing = null
                    onOpenWatched(current.app.packageName)
                },
                onRemove = {
                    viewModel.clearRule(current.app.packageName)
                    editing = null
                },
            )
        }
    }
}

@Composable
private fun AppCard(
    row: AppRow,
    showInstalled: Boolean,
    fraction: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CupertinoCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(base64 = row.app.icon, label = row.app.label, size = 40.dp, modifier = Modifier.padding(end = Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.app.label.ifBlank { row.app.packageName },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Relative usage bar (busiest app fills the track) — prototype style.
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(AppTheme.colors.separator),
                ) {
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(AppTheme.colors.accent),
                        )
                    }
                }
                if (showInstalled && row.app.installedAtMillis > 0) {
                    Text(
                        text = "Installed ${dateLabel(row.app.installedAtMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.secondaryLabel,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = Spacing.md),
            ) {
                Text(
                    text = usageLabel(row.minutes),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.secondaryLabel,
                )
                if (row.blocked) {
                    StatusChip(text = if (row.reviewing) "New · Blocked" else "Blocked", color = AppTheme.colors.destructive)
                } else if (row.supervised) {
                    StatusChip(text = if (row.reviewing) "New · Supervised" else "Supervised", color = AppTheme.colors.gold)
                } else if (row.restricted) {
                    StatusChip(text = "Restricted", color = AppTheme.colors.accent)
                }
            }
        }
    }
}

/** Top of the list while any app is watched: opens all its screenshots. */
@Composable
private fun WatchedCard(apps: Int, shotsToday: Int, onClick: () -> Unit) {
    CupertinoCard(onClick = onClick) {
        CupertinoListRow(
            title = "Supervised apps",
            subtitle = "${if (apps == 1) "1 app" else "$apps apps"} · " +
                if (shotsToday == 1) "1 screenshot today" else "$shotsToday screenshots today",
            leadingIcon = Icons.Filled.Visibility,
            showDivider = false,
            trailing = {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AppTheme.colors.secondaryLabel)
            },
        )
    }
}

@Composable
private fun EmptyApps(hasQuery: Boolean) {
    CupertinoCard(modifier = Modifier.padding(horizontal = Spacing.lg).tourTarget("activity.waiting")) {
        if (hasQuery) {
            Text("No apps match", style = MaterialTheme.typography.bodyLarge)
        } else {
            com.shahaabapps.watchdog.ui.AnimatedEmpty(
                anim = com.shahaabapps.watchdog.ui.Anim.CORGI_BOX,
                title = "Waiting for app list",
                body = "Open Shield on the child's phone — its apps appear here.",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RuleEditor(
    app: InstalledApp,
    rule: AppRule?,
    /** Just installed and awaiting review: supervised until a rule is saved. */
    newApp: Boolean,
    /** Awaiting review and held blocked (the parent's new-apps choice). */
    newAppBlocked: Boolean = false,
    onSave: (
        dailyLimitMinutes: Int?,
        blocked: Boolean,
        schedule: List<TimeWindow>,
        watchEverySeconds: Int,
        scheduleBlocks: Boolean,
    ) -> Unit,
    onRemove: () -> Unit,
    onViewShots: () -> Unit,
) {
    var access by remember {
        mutableStateOf(
            when {
                rule?.blocked == true || newAppBlocked -> Access.BLOCKED
                (rule?.watchEverySeconds ?: 0) > 0 || newApp -> Access.WATCHED
                else -> Access.ALLOWED
            },
        )
    }
    val blocked = access == Access.BLOCKED
    var watchEvery by remember { mutableStateOf(rule?.watchEverySeconds?.takeIf { it > 0 } ?: DEFAULT_WATCH_SECONDS) }
    var limit by remember { mutableStateOf(rule?.dailyLimitMinutes) }
    var windows by remember {
        mutableStateOf<List<Pair<Int, Int>>>(
            rule?.schedule?.map { it.startMinuteOfDay to it.endMinuteOfDay } ?: emptyList(),
        )
    }
    var scheduleBlocks by remember { mutableStateOf(rule?.scheduleBlocks == true) }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(base64 = app.icon, label = app.label, size = 44.dp, modifier = Modifier.padding(end = Spacing.md))
            Text(app.label.ifBlank { app.packageName }, style = MaterialTheme.typography.headlineMedium)
        }

        val accessOptions = listOf(
            Triple(Access.ALLOWED, "Allowed", "Usable within the limit and hours below."),
            Triple(Access.WATCHED, "Supervised", "Allowed, and Shield sends you screenshots of it at random moments. Your child is told."),
            Triple(Access.BLOCKED, "Blocked", "Always off-limits."),
        )
        CupertinoCard(modifier = Modifier.tourTarget("rule.block")) {
            Column {
                accessOptions.forEachIndexed { index, (option, title, subtitle) ->
                    CupertinoListRow(
                        title = title,
                        subtitle = subtitle,
                        showDivider = index < accessOptions.lastIndex,
                        onClick = { access = option },
                        trailing = {
                            if (option == access) {
                                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = AppTheme.colors.accent)
                            }
                        },
                    )
                }
            }
        }

        if (access == Access.WATCHED) {
            Text("Screenshots", style = MaterialTheme.typography.titleMedium)
            Text(
                "Taken only while the app is open, at a random moment in each interval.",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.secondaryLabel,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                WATCH_OPTIONS.forEach { (seconds, label) ->
                    FilterChip(
                        selected = watchEvery == seconds,
                        onClick = { watchEvery = seconds },
                        label = { Text(label) },
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
            if (rule?.watched == true) {
                CupertinoCard {
                    CupertinoListRow(
                        title = "View screenshots",
                        leadingIcon = Icons.Filled.Visibility,
                        showDivider = false,
                        onClick = onViewShots,
                    )
                }
            }
        }

        if (!blocked) {
            Text("Daily limit", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.tourTarget("rule.limit")) {
                LIMIT_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = limit == option,
                        onClick = { limit = option },
                        label = { Text(limitLabel(option)) },
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }

            Text("Hours", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = !scheduleBlocks,
                    onClick = { scheduleBlocks = false },
                    label = { Text("Allowed hours") },
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                FilterChip(
                    selected = scheduleBlocks,
                    onClick = { scheduleBlocks = true },
                    label = { Text("Blocked hours") },
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            Text(
                if (scheduleBlocks) {
                    "The app is blocked during these times and works the rest of the day. " +
                        "Add as many as you like; an end before the start runs overnight."
                } else {
                    "The app works only during these times. Add as many as you like; " +
                        "an end before the start runs overnight. Leave empty to allow all day."
                },
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.secondaryLabel,
            )
            CupertinoCard(modifier = Modifier.tourTarget("rule.hours")) {
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
                // Keep the limit + allowed-hours even when "Block completely" is on,
                // so toggling the block off later restores them (enforcement ignores
                // them while blocked — RuleEvaluator short-circuits on `blocked`).
                val schedule = windows.filter { it.first != it.second }
                    .map { TimeWindow(it.first, it.second, setOf(1, 2, 3, 4, 5, 6, 7)) }
                onSave(limit, blocked, schedule, if (access == Access.WATCHED) watchEvery else 0, scheduleBlocks)
            },
            modifier = Modifier.tourTarget("rule.save"),
        )
        if (rule != null) {
            CupertinoTextButton(text = "Remove rule", onClick = { confirmRemove = true }, destructive = true)
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = "Remove rule?",
            message = "This clears the limit and schedule for ${app.label.ifBlank { "this app" }}.",
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
private fun TimePickerSheet(initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (minutes: Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinutes / 60, initialMinute = initialMinutes % 60, is24Hour = false)
    com.shahaabapps.core.ui.components.AppDialog(
        onDismiss = onDismiss,
        title = "Pick a time",
        confirmText = "OK",
        onConfirm = { onConfirm(state.hour * 60 + state.minute) },
        dismissText = "Cancel",
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            TimePicker(state = state)
        }
    }
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

private fun limitLabel(minutes: Int?): String = when (minutes) {
    null -> "No limit"
    in 60..Int.MAX_VALUE -> "${minutes / 60}h"
    else -> "${minutes}m"
}

private fun usageLabel(minutes: Int): String =
    if (minutes <= 0) "Not used today" else "${formatDuration(minutes)} today"

private fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
private fun dateLabel(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter)
