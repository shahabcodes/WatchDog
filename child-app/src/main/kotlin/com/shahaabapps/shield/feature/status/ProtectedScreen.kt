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
