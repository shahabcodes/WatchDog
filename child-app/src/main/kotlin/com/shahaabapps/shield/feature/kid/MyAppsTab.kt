package com.shahaabapps.shield.feature.kid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val CARD = Color(0xFFFFFFFF)
private val NAVY = Color(0xFF00133F)
private val NAVY2 = Color(0xFF012169)
private val MUTED = Color(0xFF4A5878)
private val CHIP = Color(0xFFDCE6FF)
private val TRACK = Color(0xFFDDE3EF)

private enum class MyAppsList(val label: String, val empty: String, val icon: ImageVector) {
    BLOCKED("Blocked", "No apps are blocked.", Icons.Filled.Block),
    RESTRICTED("Restricted", "No apps have a time limit or set hours.", Icons.Filled.Schedule),
    SUPERVISED("Supervised", "No apps are supervised.", Icons.Filled.Visibility),
}

/** "My apps": which apps the parent blocked, restricted or supervises, and why. */
@Composable
internal fun KidMyApps(viewModel: MyAppsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var list by remember { mutableStateOf(MyAppsList.BLOCKED) }
    val apps = when (list) {
        MyAppsList.BLOCKED -> state.blocked
        MyAppsList.RESTRICTED -> state.restricted
        MyAppsList.SUPERVISED -> state.supervised
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("My apps", color = NAVY, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TRACK).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MyAppsList.entries.forEach { l ->
                KidSeg(l.label, list == l, Modifier.weight(1f)) { list = l }
            }
        }
        if (list == MyAppsList.SUPERVISED && apps.isNotEmpty()) {
            Text(
                "These apps work, but your parent can see screenshots of them, taken at random moments while you use them.",
                color = MUTED, fontSize = 13.sp,
            )
        }
        if (apps.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CARD).padding(vertical = 32.dp, horizontal = 16.dp),
            ) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(CHIP), contentAlignment = Alignment.Center) {
                    Icon(list.icon, contentDescription = null, tint = NAVY2, modifier = Modifier.size(28.dp))
                }
                Text(list.empty, color = NAVY, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
            }
        }
        apps.forEach { app ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CARD).padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                com.shahaabapps.core.ui.components.AppIcon(base64 = app.iconBase64, label = app.label, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.label, color = NAVY, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(app.detail, color = MUTED, fontSize = 12.sp)
                }
                if (list == MyAppsList.BLOCKED) {
                    Spacer(Modifier.width(8.dp))
                    if (app.asked) {
                        Text("Asked", color = MUTED, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(CHIP)
                                .clickable { viewModel.askSupervised(app) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text("Ask supervised", color = NAVY2, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        if (list == MyAppsList.BLOCKED && apps.isNotEmpty()) {
            Text(
                "\"Ask supervised\" asks your parent to let you use the app while they see screenshots of it.",
                color = MUTED, fontSize = 12.sp,
            )
        }
    }
}
