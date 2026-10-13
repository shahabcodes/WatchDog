package com.shahaabapps.shield.feature.kid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.model.ChildAccount
import com.shahaabapps.core.model.FocusWindow
import com.shahaabapps.shield.enforcement.EnforcementSetupCard
import com.shahaabapps.shield.enforcement.ProtectionSetupCard
import com.shahaabapps.shield.feature.ask.AskViewModel
import com.shahaabapps.shield.feature.settings.SettingsScreen
import com.shahaabapps.shield.location.AlertsSetupCard
import com.shahaabapps.shield.location.BatterySetupCard
import com.shahaabapps.shield.location.LocationSetupCard
import com.shahaabapps.shield.ui.ShieldMark
import com.shahaabapps.shield.feature.status.ProtectedViewModel
import java.time.LocalDateTime

private val BG = Color(0xFFF4F6FB)
private val CARD = Color(0xFFFFFFFF)
private val NAVY = Color(0xFF00133F)
private val NAVY2 = Color(0xFF012169)
private val GOLD = Color(0xFFF5B83D)
private val MUTED = Color(0xFF4A5878)
private val CHIP = Color(0xFFDCE6FF)
private val TRACK = Color(0xFFDDE3EF)

private enum class KidTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    TIME("My time", Icons.Filled.Schedule),
    ME("Me", Icons.Filled.Person),
}

@Composable
fun KidShell(
    child: ChildAccount,
    viewModel: ProtectedViewModel = hiltViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(child.childId) { viewModel.reportState() }

    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    var tab by remember { mutableStateOf(KidTab.HOME) }
    val kidVm: KidViewModel = hiltViewModel()
    val state by kidVm.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(BG)) {
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                KidTab.HOME -> KidHome(child = child, state = state, onSeeApps = { tab = KidTab.TIME })
                KidTab.TIME -> KidTime(state = state)
                KidTab.ME -> KidMe(child = child, onSettings = { showSettings = true })
            }
        }
        KidBottomBar(current = tab, onSelect = { tab = it })
    }
}

@Composable
private fun KidBottomBar(current: KidTab, onSelect: (KidTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CARD)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
    ) {
        KidTab.entries.forEach { t ->
            val selected = t == current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(t) }
                    .padding(vertical = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (selected) CHIP else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(t.icon, contentDescription = t.label, tint = if (selected) NAVY2 else MUTED, modifier = Modifier.size(20.dp))
                }
                Text(t.label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, color = if (selected) NAVY2 else MUTED)
            }
        }
    }
}

@Composable
private fun KidHome(child: ChildAccount, state: KidUiState, onSeeApps: () -> Unit) {
    val askVm: AskViewModel = hiltViewModel()
    val pendingAsk by askVm.pending.collectAsStateWithLifecycle()
    val activeFocus = activeFocusLabel(state.focus)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Hi ${child.profile.name.ifBlank { "there" }}", color = NAVY, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
            }
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(NAVY2).border(3.dp, GOLD, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(child.profile.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            }
        }

        // Protected card.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(NAVY).padding(16.dp),
        ) {
            ShieldMark(size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("You're protected", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    if (child.linkedParents.isEmpty()) "Connected to your family · location shared"
                    else "Connected to ${child.linkedParents.size} parent${if (child.linkedParents.size == 1) "" else "s"} · location shared",
                    color = Color(0xFFC7D3F0),
                    fontSize = 13.sp,
                )
            }
        }

        if (activeFocus != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CHIP).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = NAVY2, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(activeFocus, color = NAVY2, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }

        // Screen time card with a ring.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CARD).padding(16.dp),
        ) {
            ScreenTimeRing(usedMinutes = state.totalMinutes, capMinutes = state.apps.mapNotNull { it.limitMinutes }.sum())
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Screen time", color = NAVY, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${formatDur(state.totalMinutes)} used today.", color = MUTED, fontSize = 13.sp)
                Text("See my apps", color = NAVY2, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onSeeApps).padding(vertical = 2.dp))
            }
        }

        // Ask for more time.
        if (pendingAsk == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp)).border(2.dp, NAVY2, RoundedCornerShape(18.dp)).clickable { askVm.askMoreTime(30) },
            ) {
                Icon(Icons.Filled.MoreTime, contentDescription = null, tint = NAVY2, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ask for more time", color = NAVY2, fontWeight = FontWeight.Bold)
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CARD).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(GOLD))
                Spacer(Modifier.width(10.dp))
                Text("Asked for ${pendingAsk!!.summary}", color = NAVY, modifier = Modifier.weight(1f), fontSize = 14.sp)
                Text("Cancel", color = NAVY2, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable { pendingAsk?.let { askVm.cancel(it.id) } }.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun KidTime(state: KidUiState) {
    var week by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("My time", color = NAVY, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TRACK).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            KidSeg("Today", !week, Modifier.weight(1f)) { week = false }
            KidSeg("This week", week, Modifier.weight(1f)) { week = true }
        }
        if (!week) {
            Text(formatDur(state.totalMinutes), color = NAVY, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            if (state.apps.isEmpty()) {
                Text("No app usage recorded today yet.", color = MUTED, fontSize = 14.sp)
            }
            state.apps.forEach { app ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CARD).padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(NAVY2),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(app.label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = Color.White, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, color = NAVY, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            when {
                                app.blocked -> "Blocked"
                                app.limitMinutes != null -> "${app.minutes} of ${app.limitMinutes} min"
                                else -> "${app.minutes} min"
                            },
                            color = MUTED, fontSize = 12.sp,
                        )
                    }
                }
            }
        } else {
            val maxM = (state.weekly.maxOfOrNull { it.minutes } ?: 1).coerceAtLeast(1)
            Text("Last 7 days", color = NAVY, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(22.dp)).background(CARD).padding(16.dp),
            ) {
                state.weekly.forEach { d ->
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((120 * d.minutes / maxM).coerceAtLeast(4).dp)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(if (d.isToday) GOLD else NAVY2),
                        )
                        Text(d.label, color = MUTED, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun KidMe(child: ChildAccount, onSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Me & my family", color = NAVY, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CARD).padding(16.dp),
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(NAVY2).border(3.dp, GOLD, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(child.profile.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(child.profile.name.ifBlank { "Me" }, color = NAVY, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    if (child.linkedParents.isEmpty()) "Connected to your family"
                    else "Connected to ${child.linkedParents.size} parent${if (child.linkedParents.size == 1) "" else "s"}",
                    color = MUTED, fontSize = 13.sp,
                )
            }
        }
        Text("WHAT YOUR PARENT CAN SEE", color = MUTED, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CARD).padding(horizontal = 14.dp),
        ) {
            SeeRow(Icons.Filled.LocationOn, "Where this phone is", "Live location and today's places")
            SeeRow(Icons.Filled.Apps, "Apps and screen time", "Which apps you use, and for how long")
            SeeRow(Icons.Filled.Image, "Your screen, when asked", "A screenshot if your parent requests one")
            SeeRow(Icons.Filled.BatteryStd, "Battery level", "So they know if your phone is low", last = true)
        }
        Text("They can't read your messages or see your photos.", color = MUTED, fontSize = 12.sp)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CARD).clickable(onClick = onSettings).padding(16.dp),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = NAVY2)
            Spacer(Modifier.width(12.dp))
            Text("Settings", color = NAVY, fontWeight = FontWeight.SemiBold)
        }
        Text("SET UP", color = MUTED, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
        EnforcementSetupCard()
        LocationSetupCard()
        BatterySetupCard()
        AlertsSetupCard()
        ProtectionSetupCard()
    }
}

@Composable
private fun SeeRow(icon: ImageVector, title: String, detail: String, last: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = NAVY2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = NAVY, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(detail, color = MUTED, fontSize = 12.sp)
        }
    }
}

@Composable
private fun KidSeg(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) CARD else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = NAVY, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun ScreenTimeRing(usedMinutes: Int, capMinutes: Int) {
    val frac = if (capMinutes > 0) (usedMinutes.toFloat() / capMinutes).coerceIn(0f, 1f) else 0f
    Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(96.dp)) {
            val stroke = 12.dp.toPx()
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            drawArc(color = TRACK, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = androidx.compose.ui.geometry.Size(d, d), style = Stroke(width = stroke))
            if (frac > 0f) {
                drawArc(color = GOLD, startAngle = -90f, sweepAngle = 360f * frac, useCenter = false, topLeft = topLeft, size = androidx.compose.ui.geometry.Size(d, d), style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
        }
        Text(formatDur(usedMinutes), color = NAVY, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
}

private fun formatDur(min: Int): String {
    val h = min / 60
    val m = min % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

private fun activeFocusLabel(focus: com.shahaabapps.core.model.FocusSettings): String? {
    val now = LocalDateTime.now()
    val minute = now.hour * 60 + now.minute
    val iso = now.dayOfWeek.value
    fun on(w: FocusWindow) = w.activeAt(minute, iso)
    return when {
        on(focus.school) -> "School mode is on right now"
        on(focus.bedtime) -> "Bedtime is on — wind down"
        else -> null
    }
}
