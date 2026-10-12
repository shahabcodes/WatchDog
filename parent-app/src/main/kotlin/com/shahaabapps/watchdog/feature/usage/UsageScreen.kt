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
import androidx.compose.material.icons.filled.Add
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
import com.shahaabapps.core.ui.components.LoadingView
import com.shahaabapps.core.ui.components.SectionHeader
import com.shahaabapps.core.ui.components.StatusChip
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LIMIT_OPTIONS = listOf(null, 15, 30, 60, 120, 180)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(
    onBack: () -> Unit,
    embedded: Boolean = false,
    viewModel: UsageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AppRow?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.groupedBackground)
            .then(if (embedded) Modifier.statusBarsPadding() else Modifier),
    ) {
        if (embedded) {
            Column(modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.sm)) {
                Text(
                    "Activity",
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
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
            state.loading -> LoadingView(label = "Loading apps…", modifier = Modifier.padding(top = Spacing.xl))
            state.rows.isEmpty() -> EmptyApps(hasQuery = state.query.isNotBlank())
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                val maxMinutes = state.rows.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
                items(state.rows, key = { it.app.packageName }) { row ->
                    AppCard(
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
            RuleEditor(
                app = current.app,
                rule = current.rule,
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
}

@Composable
private fun AppCard(row: AppRow, showInstalled: Boolean, fraction: Float, onClick: () -> Unit) {
    CupertinoCard(onClick = onClick) {
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
                if (row.rule?.blocked == true) {
                    StatusChip(text = "Blocked", color = AppTheme.colors.destructive)
                } else if (row.rule?.dailyLimitMinutes != null) {
                    StatusChip(text = "Limited", color = AppTheme.colors.accent)
                }
            }
        }
    }
}

@Composable
private fun EmptyApps(hasQuery: Boolean) {
    CupertinoCard(modifier = Modifier.padding(horizontal = Spacing.lg)) {
        Text(if (hasQuery) "No apps match your search" else "Waiting for app list", style = MaterialTheme.typography.bodyLarge)
        if (!hasQuery) {
            Text(
                "Open Shield on the child's phone — its apps appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.secondaryLabel,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RuleEditor(
    app: InstalledApp,
    rule: AppRule?,
    onSave: (dailyLimitMinutes: Int?, blocked: Boolean, schedule: List<TimeWindow>) -> Unit,
    onRemove: () -> Unit,
) {
    var blocked by remember { mutableStateOf(rule?.blocked ?: false) }
    var limit by remember { mutableStateOf(rule?.dailyLimitMinutes) }
    var windows by remember {
        mutableStateOf<List<Pair<Int, Int>>>(
            rule?.schedule?.map { it.startMinuteOfDay to it.endMinuteOfDay } ?: emptyList(),
        )
    }
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

        CupertinoCard {
            CupertinoListRow(
                title = "Block completely",
                subtitle = "Always off-limits",
                showDivider = false,
                trailing = { CupertinoSwitch(checked = blocked, onCheckedChange = { blocked = it }) },
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
                    windows.filter { it.first != it.second }
                        .map { TimeWindow(it.first, it.second, setOf(1, 2, 3, 4, 5, 6, 7)) }
                }
                onSave(if (blocked) null else limit, blocked, schedule)
            },
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
