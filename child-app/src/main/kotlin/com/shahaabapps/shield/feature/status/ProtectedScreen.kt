package com.shahaabapps.shield.feature.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.MoreTime
import com.shahaabapps.core.model.TimeRequest
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.shahaabapps.core.model.ChildAccount
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.StatusChip
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Brand
import com.shahaabapps.core.ui.theme.Spacing
import com.shahaabapps.shield.enforcement.EnforcementSetupCard
import com.shahaabapps.shield.enforcement.ProtectionSetupCard
import com.shahaabapps.shield.feature.settings.SettingsScreen
import com.shahaabapps.shield.location.AlertsSetupCard
import com.shahaabapps.shield.location.BatterySetupCard
import com.shahaabapps.shield.location.LocationSetupCard

@Composable
fun ProtectedScreen(
    child: ChildAccount,
    modifier: Modifier = Modifier,
    viewModel: ProtectedViewModel = hiltViewModel(),
) {
    LaunchedEffect(child.childId) { viewModel.reportState() }

    val askVm: com.shahaabapps.shield.feature.ask.AskViewModel = hiltViewModel()
    val pendingAsk by askVm.pending.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.accent),
    ) {
        // Teal hero band: shield mark + "You're protected" + connection line.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.accent)
                .statusBarsPadding()
                .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f))
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White.copy(alpha = 0.9f))
                }
            }
            com.shahaabapps.shield.ui.ShieldMark(
                modifier = Modifier.padding(top = Spacing.sm),
                size = 84.dp,
            )
            Text(
                text = "You're protected",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.md),
            )
            Text(
                text = if (child.linkedParents.isEmpty()) {
                    "Connected to your family"
                } else {
                    "Connected to ${child.linkedParents.size} parent${if (child.linkedParents.size == 1) "" else "s"}"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        // Paper sheet: the setup checklist.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(Color(0xFFFFF6E0))
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AskCard(
                pending = pendingAsk,
                onAsk = { askVm.askMoreTime(30) },
                onCancel = { pendingAsk?.let { askVm.cancel(it.id) } },
            )
            Text(
                text = "SET UP",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = AppTheme.colors.secondaryLabel,
            )
            EnforcementSetupCard()
            LocationSetupCard()
            BatterySetupCard()
            AlertsSetupCard()
            ProtectionSetupCard()
        }
    }
}

private val LH_NAVY = Color(0xFF012169)
private val LH_GOLD = Color(0xFFF5B83D)

/** "Ask for more time" button, or a pending "waiting for an answer" row. */
@Composable
private fun AskCard(pending: TimeRequest?, onAsk: () -> Unit, onCancel: () -> Unit) {
    if (pending == null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(BorderStroke(2.dp, LH_NAVY), RoundedCornerShape(18.dp))
                .clickable(onClick = onAsk),
        ) {
            Icon(Icons.Filled.MoreTime, contentDescription = null, tint = LH_NAVY, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Ask for more time", color = LH_NAVY, fontWeight = FontWeight.Bold)
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(LH_GOLD))
            Spacer(Modifier.width(10.dp))
            Text(
                "Asked for ${pending.summary}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Cancel",
                color = LH_NAVY,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable(onClick = onCancel).padding(start = 8.dp),
            )
        }
    }
}
