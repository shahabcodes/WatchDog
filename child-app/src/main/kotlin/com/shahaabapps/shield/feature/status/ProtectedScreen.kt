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
            .background(AppTheme.colors.groupedBackground)
            .verticalScroll(rememberScrollState()),
    ) {
        ShieldTopBar(name = child.profile.name, onSettings = { showSettings = true })

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = Spacing.sm)
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Brand.accentGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(52.dp),
                )
            }

            Text(
                text = "You're protected",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Shield is active and connected to your family.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )

            CupertinoCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.secondaryLabel,
                    )
                    StatusChip(text = "Connected", color = AppTheme.colors.success)
                    Text(
                        text = "${child.linkedParents.size} parent(s) linked",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            EnforcementSetupCard()
            LocationSetupCard()
            BatterySetupCard()
            AlertsSetupCard()
            ProtectionSetupCard()
        }
    }
}

@Composable
private fun ShieldTopBar(name: String, onSettings: () -> Unit) {
    Surface(color = AppTheme.colors.card) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Hi, ${name.ifBlank { "there" }}",
                    fontSize = 13.sp,
                    color = AppTheme.colors.secondaryLabel,
                )
                Text(
                    "Shield",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = AppTheme.colors.secondaryLabel)
            }
        }
    }
}
