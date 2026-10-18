package com.shahaabapps.watchdog.feature.alerts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahaabapps.core.model.TimeRequest
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
    val items by viewModel.items.collectAsStateWithLifecycle()
    val requests by viewModel.pendingRequests.collectAsStateWithLifecycle()

    // The ask awaiting a decision (via an in-app tap or a notification button).
    // Approving opens a duration picker; declining is a simple confirm.
    var approveReq by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<TimeRequest?>(null)
    }
    var denyReq by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<TimeRequest?>(null)
    }
    // Open the right dialog straight away when arriving from a notification
    // Approve/Reject button — but only once, and only while the ask is still pending.
    var deepLinkHandled by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(false)
    }
    androidx.compose.runtime.LaunchedEffect(requests) {
        if (deepLinkHandled) return@LaunchedEffect
        val id = viewModel.initialRequestId ?: return@LaunchedEffect
        val approve = when (viewModel.initialAction) {
            "APPROVE" -> true
            "REJECT" -> false
            else -> { deepLinkHandled = true; return@LaunchedEffect }
        }
        val target = requests.firstOrNull { it.id == id } ?: return@LaunchedEffect
        if (approve) approveReq = target else denyReq = target
        deepLinkHandled = true
    }

    approveReq?.let { req ->
        ApproveRequestDialog(
            request = req,
            onApprove = { grantUntil -> viewModel.respond(req.id, true, grantUntil); approveReq = null },
            onDismiss = { approveReq = null },
        )
    }
    denyReq?.let { req ->
        com.shahaabapps.core.ui.components.ConfirmDialog(
            title = "Decline request?",
            message = "Your child is asking ${req.summary}.",
            confirmText = "Decline",
            destructive = true,
            onConfirm = { viewModel.respond(req.id, false); denyReq = null },
            onDismiss = { denyReq = null },
        )
    }

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
                "Notifications",
                fontFamily = AppFonts.display,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (items.isNotEmpty()) {
                Text(
                    "Clear all",
                    color = AppTheme.colors.accent,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.clearAll() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }

        if (items.isEmpty() && requests.isEmpty()) {
            EmptyAlerts()
        } else {
            val today = LocalDate.now()
            val grouped = items.groupBy { millisToDate(it.event.timestampMillis) == today }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.xl),
            ) {
                if (requests.isNotEmpty()) {
                    item { SectionLabel("REQUESTS") }
                    items(requests, key = { it.id }) { req ->
                        RequestCard(
                            request = req,
                            onApprove = { approveReq = req },
                            onDeny = { denyReq = req },
                        )
                    }
                }
                grouped[true]?.let { todayItems ->
                    item { SectionLabel("TODAY") }
                    item { AlertCard(todayItems) }
                }
                grouped[false]?.let { earlier ->
                    item { SectionLabel("EARLIER") }
                    item { AlertCard(earlier) }
                }
            }
        }
    }
}

/**
 * Approve dialog that lets the parent choose how long the app stays available:
 * a fixed duration from now ("For a while"), or until a specific clock time today
 * ("Until a time"). Produces an absolute wall-clock end the child enforces.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ApproveRequestDialog(
    request: TimeRequest,
    onApprove: (grantUntilMillis: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var untilMode by remember { mutableStateOf(false) }
    var durationMin by remember { mutableStateOf(60) }
    var untilMinute by remember { mutableStateOf<Int?>(null) }
    var pickTime by remember { mutableStateOf(false) }

    val grantUntil: Long? = if (!untilMode) {
        System.currentTimeMillis() + durationMin * 60_000L
    } else {
        untilMinute?.let { minuteOfDayToFutureMillis(it) }
    }

    com.shahaabapps.core.ui.components.AppDialog(
        onDismiss = onDismiss,
        title = "Approve request?",
        icon = Icons.Filled.CheckCircle,
        confirmText = "Approve",
        confirmEnabled = grantUntil != null,
        onConfirm = { grantUntil?.let(onApprove) },
    ) {
        Text(
            "Your child is asking ${request.summary}. How long should it stay available?",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.secondaryLabel,
        )
        Spacer(Modifier.height(Spacing.md))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AppTheme.colors.separator.copy(alpha = 0.4f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            ModeSeg("For a while", !untilMode, Modifier.weight(1f)) { untilMode = false }
            ModeSeg("Until a time", untilMode, Modifier.weight(1f)) { untilMode = true }
        }
        Spacer(Modifier.height(Spacing.md))
        if (!untilMode) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(15, 30, 60, 120, 240).forEach { m ->
                    FilterChip(
                        selected = durationMin == m,
                        onClick = { durationMin = m },
                        label = { Text(durationLabel(m)) },
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppTheme.colors.separator.copy(alpha = 0.3f))
                    .clickable { pickTime = true }
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    untilMinute?.let { "Until ${clockLabel(it)}" } ?: "Pick a time",
                    color = AppTheme.colors.accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    if (pickTime) {
        val initial = untilMinute ?: run {
            val c = java.util.Calendar.getInstance()
            c.add(java.util.Calendar.HOUR_OF_DAY, 1)
            c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
        }
        val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = false)
        com.shahaabapps.core.ui.components.AppDialog(
            onDismiss = { pickTime = false },
            title = "Available until",
            confirmText = "Set",
            onConfirm = { untilMinute = state.hour * 60 + state.minute; pickTime = false },
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        }
    }
}

@Composable
private fun ModeSeg(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
            color = if (selected) Color.White else AppTheme.colors.secondaryLabel,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun durationLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes == 60 -> "1 hour"
    else -> "${minutes / 60} hours"
}

private val clockFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private fun clockLabel(minuteOfDay: Int): String =
    java.time.LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(clockFmt)

/** The next time today/tomorrow matching [minuteOfDay], as wall-clock millis. */
private fun minuteOfDayToFutureMillis(minuteOfDay: Int): Long {
    val c = java.util.Calendar.getInstance()
    c.set(java.util.Calendar.HOUR_OF_DAY, minuteOfDay / 60)
    c.set(java.util.Calendar.MINUTE, minuteOfDay % 60)
    c.set(java.util.Calendar.SECOND, 0)
    c.set(java.util.Calendar.MILLISECOND, 0)
    if (c.timeInMillis <= System.currentTimeMillis()) c.add(java.util.Calendar.DAY_OF_YEAR, 1)
    return c.timeInMillis
}

@Composable
private fun RequestCard(
    request: com.shahaabapps.core.model.TimeRequest,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = AppTheme.colors.card,
        border = BorderStroke(1.dp, AppTheme.colors.accent.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your child is asking ${request.summary}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppTheme.colors.accent)
                        .clickable(onClick = onApprove),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Approve", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(BorderStroke(1.dp, AppTheme.colors.separator), RoundedCornerShape(14.dp))
                        .clickable(onClick = onDeny),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Not now", color = AppTheme.colors.secondaryLabel, fontWeight = FontWeight.Bold)
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
private fun AlertCard(items: List<AlertItem>) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = AppTheme.colors.card,
        border = BorderStroke(1.dp, AppTheme.colors.separator),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            items.forEachIndexed { i, item ->
                AlertRow(item)
                if (i != items.lastIndex) {
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
private fun AlertRow(item: AlertItem) {
    val event = item.event
    val entered = event.transition == GeofenceTransition.ENTER
    // "Arrived at Home" for a saved place; a clearly different phrase when the
    // place isn't one of the saved locations.
    val title = when {
        item.known && item.placeName != null ->
            if (entered) "Arrived at ${item.placeName}" else "Left ${item.placeName}"
        else ->
            if (entered) "Arrived at an unknown place" else "Left an unknown place"
    }
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
                title,
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
