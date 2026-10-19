package com.shahaabapps.shield.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import com.shahaabapps.core.ui.components.AboutSection
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.CupertinoListRow
import com.shahaabapps.core.ui.components.GuardianHeader
import com.shahaabapps.core.ui.components.ThemeSettingsSection
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing
import com.shahaabapps.shield.feature.pairing.QrCodeGenerator
import com.shahaabapps.shield.ui.ThemeViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: ThemeViewModel = hiltViewModel(),
    secureCodeViewModel: SecureCodeViewModel = hiltViewModel(),
) {
    val mode by viewModel.themeMode.collectAsStateWithLifecycle()
    val palette by viewModel.accentPalette.collectAsStateWithLifecycle()
    val qrContent by secureCodeViewModel.qrContent.collectAsStateWithLifecycle()
    val resyncBusy by secureCodeViewModel.resyncBusy.collectAsStateWithLifecycle()
    var showCode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.groupedBackground)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        GuardianHeader(title = "Settings", onBack = onBack)
        ThemeSettingsSection(
            mode = mode,
            palette = palette,
            onModeChange = viewModel::setThemeMode,
            onPaletteChange = viewModel::setAccentPalette,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
        CupertinoCard(modifier = Modifier.padding(horizontal = Spacing.lg)) {
            CupertinoListRow(
                title = "Show secure code",
                subtitle = "Let a parent scan this to verify the encrypted connection",
                leadingIcon = Icons.Filled.QrCode2,
                showDivider = true,
                onClick = { showCode = true },
            )
            CupertinoListRow(
                title = "Re-sync security",
                subtitle = if (resyncBusy) "Re-sharing your key with the parent…"
                else "Fixes a parent stuck on “can't see location”",
                leadingIcon = Icons.Filled.Sync,
                showDivider = false,
                onClick = { if (!resyncBusy) secureCodeViewModel.resyncSecurity() },
            )
        }
        AboutSection(
            appName = "Shield",
            tagline = "Keeps this device safe and connected to your family.",
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
    }

    if (showCode) {
        val content = qrContent
        com.shahaabapps.core.ui.components.AppDialog(
            onDismiss = { showCode = false },
            title = "Secure code",
            icon = Icons.Filled.QrCode2,
            dismissText = "Done",
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "In Watch Dog, tap \"Verify\" on this child, then scan this code.",
                    color = AppTheme.colors.secondaryLabel,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                if (content != null) {
                    Image(
                        bitmap = QrCodeGenerator.generate(content, sizePx = 520),
                        contentDescription = "Secure pairing code",
                        modifier = Modifier.padding(top = Spacing.md).size(240.dp),
                    )
                } else {
                    Text(
                        "Preparing…",
                        modifier = Modifier.padding(top = Spacing.md),
                        color = AppTheme.colors.secondaryLabel,
                    )
                }
            }
        }
    }
}
