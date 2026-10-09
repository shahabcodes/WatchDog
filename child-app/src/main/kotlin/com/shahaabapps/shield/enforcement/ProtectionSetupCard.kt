package com.shahaabapps.shield.enforcement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.CupertinoListRow
import com.shahaabapps.core.ui.components.StatusChip
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

/**
 * Shown only when a parent has turned on uninstall protection: prompts the child
 * device to enable Shield as a device admin (which disables the Uninstall button).
 */
@Composable
fun ProtectionSetupCard(
    modifier: Modifier = Modifier,
    viewModel: ProtectionStatusViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    if (!settings.uninstallBlocked) return

    val context = LocalContext.current
    var adminActive by remember { mutableStateOf(DeviceAdmin.isActive(context)) }
    LifecycleResumeEffect(Unit) {
        adminActive = DeviceAdmin.isActive(context)
        onPauseOrDispose { }
    }

    CupertinoCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (adminActive) {
                Text("Uninstall protection", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.secondaryLabel)
                StatusChip(text = "Active", color = AppTheme.colors.success)
            } else {
                Text("Protect this app", style = MaterialTheme.typography.titleMedium)
                Text(
                    "A parent turned on uninstall protection. Enable device admin so Shield can't be removed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.secondaryLabel,
                )
                CupertinoListRow(
                    title = "Device admin",
                    showDivider = false,
                    leadingIcon = Icons.Filled.Lock,
                    trailing = {
                        Text(
                            text = "Enable",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppTheme.colors.accent,
                            modifier = Modifier
                                .clickable { context.startActivity(DeviceAdmin.addIntent(context)) }
                                .padding(horizontal = Spacing.sm, vertical = 4.dp),
                        )
                    },
                )
            }
        }
    }
}
