package com.shahaabapps.shield.location

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.CupertinoListRow
import com.shahaabapps.core.ui.components.StatusChip
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

/**
 * Surfaces the alerting permissions Shield needs so the parent can reach the
 * child: showing notifications and launching the full-screen ring alarm.
 * Notifications are requested automatically the first time this appears.
 */
@Composable
fun AlertsSetupCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var notificationsGranted by remember { mutableStateOf(RingPermissions.hasNotifications(context)) }
    var fullScreenGranted by remember { mutableStateOf(RingPermissions.canUseFullScreen(context)) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { notificationsGranted = RingPermissions.hasNotifications(context) }

    // Ask for notifications automatically the first time setup is shown.
    LaunchedEffect(Unit) {
        if (RingPermissions.notificationsRequestable() && !notificationsGranted) {
            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LifecycleResumeEffect(Unit) {
        notificationsGranted = RingPermissions.hasNotifications(context)
        fullScreenGranted = RingPermissions.canUseFullScreen(context)
        onPauseOrDispose { }
    }

    if (notificationsGranted && fullScreenGranted) {
        CupertinoCard(modifier = modifier) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Alerts", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.secondaryLabel)
                StatusChip(text = "Ready", color = AppTheme.colors.success)
            }
        }
        return
    }

    CupertinoCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Enable alerts", style = MaterialTheme.typography.titleMedium)
            Text(
                "So your family can reach you — show notifications and let the ring alarm appear full-screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.secondaryLabel,
            )
            if (RingPermissions.notificationsRequestable()) {
                PermissionRow(
                    title = "Notifications",
                    granted = notificationsGranted,
                    onEnable = {
                        notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    },
                )
            }
            if (RingPermissions.fullScreenRequestable()) {
                PermissionRow(
                    title = "Full-screen ring alarm",
                    granted = fullScreenGranted,
                    onEnable = {
                        context.startActivity(RingPermissions.fullScreenSettingsIntent(context))
                    },
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, onEnable: () -> Unit) {
    CupertinoListRow(
        title = title,
        showDivider = false,
        trailing = {
            if (granted) {
                StatusChip(text = "Granted", color = AppTheme.colors.success)
            } else {
                Text(
                    text = "Enable",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppTheme.colors.accent,
                    modifier = Modifier
                        .clickable(onClick = onEnable)
                        .padding(horizontal = Spacing.sm, vertical = 4.dp),
                )
            }
        },
        leadingIcon = if (granted) Icons.Filled.CheckCircle else null,
    )
}
