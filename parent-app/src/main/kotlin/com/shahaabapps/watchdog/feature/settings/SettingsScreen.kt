package com.shahaabapps.watchdog.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.shahaabapps.core.ui.components.Hero
import com.shahaabapps.core.ui.components.HeroIconButton
import com.shahaabapps.core.ui.components.ThemeSettingsSection
import com.shahaabapps.core.ui.theme.Spacing

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val mode by viewModel.themeMode.collectAsStateWithLifecycle()
    val palette by viewModel.accentPalette.collectAsStateWithLifecycle()
    val resetMessage by viewModel.resetMessage.collectAsStateWithLifecycle()
    val sendingReset by viewModel.sendingReset.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Hero(
            title = "Settings",
            actions = {
                HeroIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            },
        )

        ThemeSettingsSection(
            mode = mode,
            palette = palette,
            onModeChange = viewModel::setThemeMode,
            onPaletteChange = viewModel::setAccentPalette,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )

        CupertinoCard(modifier = Modifier.padding(horizontal = Spacing.lg)) {
            if (viewModel.email != null) {
                CupertinoListRow(
                    title = if (sendingReset) "Sending reset link…" else "Reset password",
                    subtitle = viewModel.email,
                    leadingIcon = Icons.Filled.LockReset,
                    onClick = { if (!sendingReset) confirmReset = true },
                )
            }
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
        AlertDialog(
            onDismissRequest = viewModel::clearResetMessage,
            confirmButton = { TextButton(onClick = viewModel::clearResetMessage) { Text("OK") } },
            title = { Text("Password reset") },
            text = { Text(message) },
        )
    }
}
