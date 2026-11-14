package com.shahaabapps.watchdog.feature.watch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.shahaabapps.core.ui.components.AppIcon
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.Hero
import com.shahaabapps.core.ui.components.HeroIconButton
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing
import com.shahaabapps.watchdog.feature.screenshot.LocalShot
import com.shahaabapps.watchdog.feature.screenshot.ScreenshotViewer
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun keyOf(e: WatchEntry) = "${e.packageName}/${e.capturedAtMillis}"

/** What the delete dialog is asking about. */
private sealed class DeleteAsk(val entries: List<WatchEntry>) {
    class Selected(entries: List<WatchEntry>) : DeleteAsk(entries)
    class Visit(entries: List<WatchEntry>) : DeleteAsk(entries)
    class All(entries: List<WatchEntry>, val app: String?) : DeleteAsk(entries)
}

/**
 * Screenshots of supervised apps, grouped by visit, newest first. Long-press a
 * screenshot to select it; then tap others (in any visit) or a visit's checkbox.
 */
@Composable
fun WatchShotsScreen(
    onBack: () -> Unit,
    viewModel: WatchShotsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    /** The shot open in the viewer, by its file. */
    var viewing by remember { mutableStateOf<File?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf<DeleteAsk?>(null) }
    fun labelOf(pkg: String) = state.appsByPackage[pkg]?.label?.ifBlank { null } ?: pkg

    val allEntries = remember(state.sessions) { state.sessions.flatMap { it.entries } }
    // Drop selections of shots no longer shown (deleted, or another app picked).
    LaunchedEffect(allEntries) {
        val keys = allEntries.mapTo(HashSet(), ::keyOf)
        selected = selected.filterTo(HashSet()) { it in keys }
    }
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selected = emptySet() }
    fun toggle(e: WatchEntry) {
        val k = keyOf(e)
        selected = if (k in selected) selected - k else selected + k
    }

    Column(Modifier.fillMaxSize().background(AppTheme.colors.groupedBackground)) {
        Hero(
            title = when {
                selecting -> "${selected.size} selected"
                else -> state.selectedApp?.let(::labelOf) ?: "Supervised apps"
            },
            subtitle = if (selecting) "Tap screenshots, or a visit's box, to select more" else "Screenshots at random moments",
            actions = {
                if (selecting) {
                    HeroIconButton(onClick = { selected = emptySet() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel selection", tint = Color.White)
                    }
                    HeroIconButton(onClick = { selected = allEntries.mapTo(HashSet(), ::keyOf) }) {
                        Icon(Icons.Filled.SelectAll, contentDescription = "Select all", tint = Color.White)
                    }
                    HeroIconButton(onClick = { confirm = DeleteAsk.Selected(allEntries.filter { keyOf(it) in selected }) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete selected", tint = Color.White)
                    }
                } else {
                    HeroIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    if (allEntries.isNotEmpty()) {
                        HeroIconButton(onClick = { confirm = DeleteAsk.All(allEntries, state.selectedApp?.let(::labelOf)) }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Delete all screenshots", tint = Color.White)
                        }
                    }
                }
            },
        )
        if (!selecting && (state.apps.size > 1 || (state.selectedApp != null && state.apps.isNotEmpty()))) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                AppChip("All apps", state.selectedApp == null) { viewModel.select(null) }
                state.apps.forEach { pkg -> AppChip(labelOf(pkg), state.selectedApp == pkg) { viewModel.select(pkg) } }
            }
        }
        if (state.sessions.isEmpty()) {
            CupertinoCard(modifier = Modifier.padding(Spacing.lg)) {
                Text("No screenshots yet", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "They arrive while your child uses a supervised app and Shield is up to date. " +
                        "Each one stays on the server for 3 days, so open Watch Dog at least that often.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(state.sessions, key = { "${it.packageName}/${it.endMillis}" }) { session ->
                    SessionCard(
                        session = session,
                        label = labelOf(session.packageName),
                        icon = state.appsByPackage[session.packageName]?.icon,
                        selecting = selecting,
                        selected = selected,
                        onTap = { e -> if (selecting) toggle(e) else if (e.file != null) viewing = e.file },
                        onLongPress = ::toggle,
                        onToggleVisit = {
                            val keys = session.entries.map(::keyOf)
                            selected = if (keys.all { it in selected }) selected - keys.toSet() else selected + keys
                        },
                        onDeleteVisit = { confirm = DeleteAsk.Visit(session.entries) },
                    )
                }
            }
        }
    }

    viewing?.let { file ->
        // Every shot on screen, in screen order (visits top to bottom, each visit's
        // shots left to right), so swiping carries on into the next visit.
        val ordered = state.sessions.flatMap { it.entries.asReversed() }.filter { it.file != null }
        val shots = ordered.map { LocalShot(it.file!!, it.capturedAtMillis) }
        val packages = ordered.associate { it.file!! to it.packageName }
        ScreenshotViewer(
            shots = shots,
            startIndex = ordered.indexOfFirst { it.file == file }.coerceAtLeast(0),
            onClose = { viewing = null },
            onDelete = { shot ->
                // Stay in the viewer, on the next shot (the previous one at the end).
                val i = ordered.indexOfFirst { it.file == shot.file }
                if (i >= 0) {
                    viewing = (ordered.getOrNull(i + 1) ?: ordered.getOrNull(i - 1))?.file
                    viewModel.delete(ordered[i])
                }
            },
            caption = { shot ->
                "${labelOf(packages[shot.file].orEmpty())} · ${dayOf(shot.capturedAtMillis)}, ${timeOf(shot.capturedAtMillis)}"
            },
        )
    }

    confirm?.let { ask ->
        val n = ask.entries.size
        val what = if (n == 1) "1 screenshot" else "$n screenshots"
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = {
                Text(
                    when (ask) {
                        is DeleteAsk.All -> if (ask.app != null) "Delete all ${ask.app} screenshots?" else "Delete all screenshots?"
                        is DeleteAsk.Visit -> "Delete this visit?"
                        is DeleteAsk.Selected -> "Delete $what?"
                    },
                )
            },
            text = {
                Text(
                    (if (ask is DeleteAsk.Selected) "" else "$what will be deleted. ") +
                        "They're removed from this phone and won't download again. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(ask.entries)
                    selected = emptySet()
                    confirm = null
                }) { Text("Delete", color = AppTheme.colors.destructive) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AppChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AppTheme.colors.accent.copy(alpha = 0.15f),
            selectedLabelColor = AppTheme.colors.accent,
        ),
    )
}

@Composable
private fun SessionCard(
    session: WatchSession,
    label: String,
    icon: String?,
    selecting: Boolean,
    selected: Set<String>,
    onTap: (WatchEntry) -> Unit,
    onLongPress: (WatchEntry) -> Unit,
    onToggleVisit: () -> Unit,
    onDeleteVisit: () -> Unit,
) {
    CupertinoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(base64 = icon, label = label, size = 32.dp, modifier = Modifier.padding(end = Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                val count = session.entries.size
                Text(
                    "${dayOf(session.startMillis)}, ${timeOf(session.startMillis)}" +
                        (if (count > 1) " – ${timeOf(session.endMillis)}" else "") +
                        " · " + if (count == 1) "1 screenshot" else "$count screenshots",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.secondaryLabel,
                )
            }
            if (selecting) {
                val picked = session.entries.count { keyOf(it) in selected }
                TriStateCheckbox(
                    state = when (picked) {
                        0 -> ToggleableState.Off
                        session.entries.size -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                    },
                    onClick = onToggleVisit,
                )
            } else {
                IconButton(onClick = onDeleteVisit) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete this visit", tint = AppTheme.colors.secondaryLabel)
                }
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(top = Spacing.sm),
        ) {
            items(session.entries.size) { i ->
                // Oldest first, left to right, like a timeline.
                val entry = session.entries[session.entries.lastIndex - i]
                Thumb(
                    entry = entry,
                    selecting = selecting,
                    checked = keyOf(entry) in selected,
                    onClick = { onTap(entry) },
                    onLongClick = { onLongPress(entry) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Thumb(
    entry: WatchEntry,
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(96.dp)
                .height(170.dp)
                .clip(shape)
                .background(AppTheme.colors.separator.copy(alpha = 0.3f))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            if (entry.file != null) {
                AsyncImage(
                    model = entry.file,
                    contentDescription = "Screenshot",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Spacing.sm)) {
                    Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = AppTheme.colors.secondaryLabel)
                    Text(
                        "This app hides its screen",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.secondaryLabel,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (checked) {
                Box(Modifier.fillMaxSize().border(3.dp, AppTheme.colors.accent, shape))
            }
            if (selecting) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (checked) AppTheme.colors.accent else Color.Black.copy(alpha = 0.35f))
                        .border(1.5.dp, Color.White, CircleShape),
                ) {
                    if (checked) {
                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Text(
            timeOf(entry.capturedAtMillis),
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.secondaryLabel,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

private fun timeOf(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME)

private fun dayOf(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DAY)
    }
}
