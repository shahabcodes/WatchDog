package com.shahaabapps.watchdog.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.ui.components.AboutSection
import com.shahaabapps.core.ui.components.ConfirmDialog
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.CupertinoListRow
import com.shahaabapps.core.ui.components.GuardianHeader
import com.shahaabapps.core.ui.components.ThemeSettingsSection
import androidx.compose.foundation.background
import com.shahaabapps.core.ui.components.SectionHeader
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing
import com.shahaabapps.watchdog.util.MapStyle

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSecurity: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val mode by viewModel.themeMode.collectAsStateWithLifecycle()
    val palette by viewModel.accentPalette.collectAsStateWithLifecycle()
    val mapStyle by viewModel.mapStyle.collectAsStateWithLifecycle()
    val resetMessage by viewModel.resetMessage.collectAsStateWithLifecycle()
    val sendingReset by viewModel.sendingReset.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }

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

        Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
            SectionHeader(
                text = "Map style",
                modifier = Modifier.padding(start = Spacing.md, bottom = Spacing.xs),
            )
            CupertinoCard {
                val styles = MapStyle.entries
                styles.forEachIndexed { index, style ->
                    CupertinoListRow(
                        title = style.label,
                        subtitle = style.description,
                        leadingIcon = Icons.Filled.Map,
                        showDivider = index < styles.lastIndex,
                        onClick = { viewModel.setMapStyle(style) },
                        trailing = {
                            if (style == mapStyle) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = AppTheme.colors.accent,
                                )
                            }
                        },
                    )
                }
            }
        }

        CupertinoCard(modifier = Modifier.padding(horizontal = Spacing.lg)) {
            if (viewModel.email != null) {
                CupertinoListRow(
                    title = "Two-factor authentication",
                    subtitle = "Protect sign-in with an authenticator app",
                    leadingIcon = Icons.Filled.Shield,
                    onClick = onOpenSecurity,
                )
                CupertinoListRow(
                    title = if (sendingReset) "Sending reset link…" else "Reset password",
                    subtitle = viewModel.email,
                    leadingIcon = Icons.Filled.LockReset,
                    onClick = { if (!sendingReset) confirmReset = true },
                )
            }
            CupertinoListRow(
                title = "Diagnostics",
                subtitle = "Crash & bug reports to help fix issues",
                leadingIcon = Icons.Filled.BugReport,
                onClick = onOpenDiagnostics,
            )
            CupertinoListRow(
                title = "Sign out",
                leadingIcon = Icons.Filled.Logout,
                showDivider = false,
                onClick = viewModel::signOut,
            )
        }

        AboutSection(
            appName = "Watch Dog",
            tagline = "Parental controls for your family.",
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Reset password?",
            message = "We'll email a password reset link to ${viewModel.email}.",
            confirmText = "Send link",
            onConfirm = { confirmReset = false; viewModel.sendPasswordReset() },
            onDismiss = { confirmReset = false },
        )
    }

    resetMessage?.let { message ->
        com.shahaabapps.core.ui.components.AppDialog(
            onDismiss = viewModel::clearResetMessage,
            title = "Password reset",
            icon = Icons.Filled.LockReset,
            dismissText = "OK",
        ) {
            Text(
                message,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = AppTheme.colors.secondaryLabel,
            )
        }
    }
}
