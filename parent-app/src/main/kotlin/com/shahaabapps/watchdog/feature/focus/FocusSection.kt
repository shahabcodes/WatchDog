package com.shahaabapps.watchdog.feature.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahaabapps.core.model.FocusSettings
import com.shahaabapps.core.model.FocusWindow
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.CupertinoPrimaryButton
import com.shahaabapps.core.ui.components.CupertinoSwitch
import com.shahaabapps.core.ui.components.StatusChip
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

private val BEDTIME_TINT = Color(0xFF7C6FF0)
private val SCHOOL_TINT = Color(0xFFF59E0B)
private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

private fun clock(minute: Int): String {
    val h = (minute / 60) % 24
    val m = minute % 60
    val ampm = if (h < 12) "AM" else "PM"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "%d:%02d %s".format(h12, m, ampm)
}

private fun daysSummary(days: Set<Int>): String = when {
    days.size == 7 -> "Every day"
    days == setOf(1, 2, 3, 4, 5) -> "Weekdays"
    days == setOf(6, 7) -> "Weekends"
    days.isEmpty() -> "No days"
    else -> days.sorted().joinToString(" ") { DAY_LABELS[it - 1] }
}

/** Compact summary card shown on the dashboard; tap to edit. */
@Composable
fun FocusCard(settings: FocusSettings, onEdit: () -> Unit) {
    CupertinoCard(onClick = onEdit) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            FocusSummaryRow(
                icon = Icons.Filled.Bedtime,
                tint = BEDTIME_TINT,
                title = "Bedtime",
                window = settings.bedtime,
            )
            FocusSummaryRow(
                icon = Icons.Filled.School,
                tint = SCHOOL_TINT,
                title = "School",
                window = settings.school,
            )
        }
    }
}

@Composable
private fun FocusSummaryRow(icon: ImageVector, tint: Color, title: String, window: FocusWindow) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = tint)
        }
        Column(modifier = Modifier.weight(1f).padding(start = Spacing.md)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (window.enabled) {
                    "${clock(window.startMinute)} – ${clock(window.endMinute)} · ${daysSummary(window.days)}"
                } else {
                    "Off · tap to schedule"
                },
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.secondaryLabel,
            )
        }
        StatusChip(
            text = if (window.enabled) "On" else "Off",
            color = if (window.enabled) AppTheme.colors.success else AppTheme.colors.secondaryLabel,
        )
    }
}

/** Bottom-sheet editor body for both focus windows. */
@Composable
fun FocusEditor(
    initial: FocusSettings,
    installedApps: List<com.shahaabapps.core.model.InstalledApp>,
    onSave: (FocusSettings) -> Unit,
) {
    var bedtime by remember { mutableStateOf(initial.bedtime) }
    var school by remember { mutableStateOf(initial.school) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Focus modes", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Apps are blocked on the child's phone during these hours. Phone calls still work, " +
                "plus any apps you allow for each mode.",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.secondaryLabel,
        )
        WindowEditor(
            icon = Icons.Filled.Bedtime,
            tint = BEDTIME_TINT,
            title = "Bedtime",
            window = bedtime,
            installedApps = installedApps,
            onChange = { bedtime = it },
        )
        WindowEditor(
            icon = Icons.Filled.School,
            tint = SCHOOL_TINT,
            title = "School",
            window = school,
            installedApps = installedApps,
            onChange = { school = it },
        )
        CupertinoPrimaryButton(
            text = "Save",
            onClick = { onSave(FocusSettings(bedtime = bedtime, school = school)) },
        )
    }
}

@Composable
private fun WindowEditor(
    icon: ImageVector,
    tint: Color,
    title: String,
    window: FocusWindow,
    installedApps: List<com.shahaabapps.core.model.InstalledApp>,
    onChange: (FocusWindow) -> Unit,
) {
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var pickApps by remember { mutableStateOf(false) }

    CupertinoCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(icon, contentDescription = null, tint = tint)
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = Spacing.md),
                )
                CupertinoSwitch(
                    checked = window.enabled,
                    onCheckedChange = { onChange(window.copy(enabled = it)) },
                )
            }

            if (window.enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TimeField(label = "Start", minute = window.startMinute, tint = tint, onClick = { pickStart = true }, modifier = Modifier.weight(1f))
                    TimeField(label = "End", minute = window.endMinute, tint = tint, onClick = { pickEnd = true }, modifier = Modifier.weight(1f))
                }
                DayPicker(days = window.days, tint = tint, onToggle = { day ->
                    val next = window.days.toMutableSet().apply { if (!add(day)) remove(day) }
                    onChange(window.copy(days = next))
                })
                // Allowed apps: stay usable while this window is active.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { pickApps = true }
                        .background(tint.copy(alpha = 0.10f))
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = Spacing.md)) {
                        Text("Allowed apps", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (window.allowedPackages.isEmpty()) "None — everything is blocked"
                            else "${window.allowedPackages.size} app${if (window.allowedPackages.size == 1) "" else "s"} stay usable",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.secondaryLabel,
                        )
                    }
                    androidx.compose.material3.Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = AppTheme.colors.secondaryLabel,
                    )
                }
            }
        }
    }

    if (pickApps) {
        AllowedAppsDialog(
            title = "$title · allowed apps",
            apps = installedApps,
            selected = window.allowedPackages,
            tint = tint,
            onDismiss = { pickApps = false },
            onConfirm = { onChange(window.copy(allowedPackages = it)); pickApps = false },
        )
    }

    if (pickStart) {
        TimePickerDialog(
            initialMinute = window.startMinute,
            onDismiss = { pickStart = false },
            onConfirm = { onChange(window.copy(startMinute = it)); pickStart = false },
        )
    }
    if (pickEnd) {
        TimePickerDialog(
            initialMinute = window.endMinute,
            onDismiss = { pickEnd = false },
            onConfirm = { onChange(window.copy(endMinute = it)); pickEnd = false },
        )
    }
}

@Composable
private fun TimeField(label: String, minute: Int, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text(label, fontSize = 12.sp, color = AppTheme.colors.secondaryLabel)
        Text(clock(minute), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

@Composable
private fun DayPicker(days: Set<Int>, tint: Color, onToggle: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        (1..7).forEach { day ->
            val on = day in days
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (on) tint else tint.copy(alpha = 0.10f))
                    .clickable { onToggle(day) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    DAY_LABELS[day - 1],
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) Color.White else AppTheme.colors.secondaryLabel,
                )
            }
        }
    }
}

/** Multi-select dialog of installed apps to allow during a focus window. */
@Composable
private fun AllowedAppsDialog(
    title: String,
    apps: List<com.shahaabapps.core.model.InstalledApp>,
    selected: Set<String>,
    tint: Color,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var picked by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(picked) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            if (apps.isEmpty()) {
                Text(
                    "No apps yet. Open Shield on the child's phone so its apps appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.secondaryLabel,
                )
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                ) {
                    androidx.compose.foundation.lazy.items(apps, key = { it.packageName }) { app ->
                        val on = app.packageName in picked
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    picked = picked.toMutableSet().apply { if (!add(app.packageName)) remove(app.packageName) }
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            com.shahaabapps.core.ui.components.AppIcon(
                                base64 = app.icon,
                                label = app.label,
                                size = 32.dp,
                                modifier = Modifier.padding(end = Spacing.md),
                            )
                            Text(
                                app.label.ifBlank { app.packageName },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            androidx.compose.material3.Checkbox(
                                checked = on,
                                onCheckedChange = {
                                    picked = picked.toMutableSet().apply { if (!add(app.packageName)) remove(app.packageName) }
                                },
                                colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = tint),
                            )
                        }
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = (initialMinute / 60) % 24,
        initialMinute = initialMinute % 60,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
    )
}
