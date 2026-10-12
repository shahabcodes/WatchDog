package com.shahaabapps.watchdog.feature.alerts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.model.GeofenceEvent
import com.shahaabapps.core.model.GeofenceTransition
import com.shahaabapps.core.ui.theme.AppFonts
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AlertsScreen(
    onBack: () -> Unit,
    viewModel: AlertsViewModel = hiltViewModel(),
) {
    val events by viewModel.events.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.groupedBackground)
            .statusBarsPadding()
            .padding(horizontal = Spacing.lg),
    ) {
        // Header: back circle + "Alerts" title.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.md),
        ) {
            Surface(
                shape = CircleShape,
                color = AppTheme.colors.card,
                border = BorderStroke(1.dp, AppTheme.colors.separator),
                modifier = Modifier.size(44.dp).clickable(onClick = onBack),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.size(14.dp))
            Text(
                "Alerts",
                fontFamily = AppFonts.display,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (events.isEmpty()) {
            EmptyAlerts()
        } else {
            val today = LocalDate.now()
            val grouped = events.groupBy { millisToDate(it.timestampMillis) == today }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.xl),
            ) {
                grouped[true]?.let { todayEvents ->
                    item { SectionLabel("TODAY") }
                    item { AlertCard(todayEvents) }
                }
                grouped[false]?.let { earlier ->
                    item { SectionLabel("EARLIER") }
                    item { AlertCard(earlier) }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = AppTheme.colors.secondaryLabel,
        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
    )
}

@Composable
private fun AlertCard(events: List<GeofenceEvent>) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = AppTheme.colors.card,
        border = BorderStroke(1.dp, AppTheme.colors.separator),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            events.forEachIndexed { i, event ->
                AlertRow(event)
                if (i != events.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(AppTheme.colors.separator.copy(alpha = 0.6f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertRow(event: GeofenceEvent) {
    val entered = event.transition == GeofenceTransition.ENTER
    val place = event.geofenceName.ifBlank { "a place" }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    if (entered) AppTheme.colors.accent.copy(alpha = 0.16f)
                    else AppTheme.colors.separator.copy(alpha = 0.5f),
                ),
        ) {
            Icon(
                if (entered) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                tint = if (entered) AppTheme.colors.accent else AppTheme.colors.secondaryLabel,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (entered) "Arrived at $place" else "Left $place",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                timeLabel(event.timestampMillis),
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.secondaryLabel,
            )
        }
    }
}

@Composable
private fun EmptyAlerts() {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = AppTheme.colors.card,
        border = BorderStroke(1.dp, AppTheme.colors.separator),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("No alerts yet", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(
                "You'll see arrivals and departures here as your child moves between saved places.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.secondaryLabel,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

private fun millisToDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val dateFmt = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())

private fun timeLabel(millis: Long): String {
    if (millis <= 0) return ""
    val zdt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return if (zdt.toLocalDate() == LocalDate.now()) zdt.format(timeFmt) else zdt.format(dateFmt)
}
