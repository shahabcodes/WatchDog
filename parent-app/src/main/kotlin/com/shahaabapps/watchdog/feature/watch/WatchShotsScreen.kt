package com.shahaabapps.watchdog.feature.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Screenshots of supervised apps, grouped by visit, newest first. */
@Composable
fun WatchShotsScreen(
    onBack: () -> Unit,
    viewModel: WatchShotsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var viewing by remember { mutableStateOf<Pair<WatchSession, Int>?>(null) }
    fun labelOf(pkg: String) = state.appsByPackage[pkg]?.label?.ifBlank { null } ?: pkg

    Column(Modifier.fillMaxSize().background(AppTheme.colors.groupedBackground)) {
        Hero(
            title = state.selectedApp?.let(::labelOf) ?: "Supervised apps",
            subtitle = "Screenshots at random moments",
            actions = {
                HeroIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            },
        )
        if (state.apps.size > 1 || (state.selectedApp != null && state.apps.isNotEmpty())) {
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
                        onOpen = { index -> viewing = session to index },
                    )
                }
            }
        }
    }

    viewing?.let { (session, index) ->
        val withImages = session.entries.filter { it.file != null }
        val shots = withImages.map { LocalShot(it.file!!, it.capturedAtMillis) }
        val start = withImages.indexOf(session.entries[index]).coerceAtLeast(0)
        ScreenshotViewer(
            shots = shots,
            startIndex = start,
            onClose = { viewing = null },
            onDelete = { shot ->
                withImages.firstOrNull { it.file == shot.file }?.let(viewModel::delete)
                viewing = null
            },
            caption = { shot -> "${labelOf(session.packageName)} · ${timeOf(shot.capturedAtMillis)}" },
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
private fun SessionCard(session: WatchSession, label: String, icon: String?, onOpen: (index: Int) -> Unit) {
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
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(top = Spacing.sm),
        ) {
            items(session.entries.size) { i ->
                // Oldest first, left to right, like a timeline.
                val index = session.entries.lastIndex - i
                Thumb(session.entries[index]) { onOpen(index) }
            }
        }
    }
}

@Composable
private fun Thumb(entry: WatchEntry, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(96.dp)
                .height(170.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppTheme.colors.separator.copy(alpha = 0.3f))
                .then(if (entry.file != null) Modifier.clickable(onClick = onClick) else Modifier),
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
