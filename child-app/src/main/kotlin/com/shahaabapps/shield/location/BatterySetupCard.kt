package com.shahaabapps.shield.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.CupertinoListRow
import com.shahaabapps.core.ui.components.StatusChip
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

/**
 * Asks the user to exempt Shield from battery optimization and points them at the
 * OEM autostart screen, so the OS won't kill the location/enforcement services in
 * the background. This is the single biggest reliability win on aggressive OEMs
 * (Xiaomi, Oppo, Vivo, Samsung, Huawei).
 */
@Composable
fun BatterySetupCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var exempt by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    LifecycleResumeEffect(Unit) {
        exempt = isIgnoringBatteryOptimizations(context)
        onPauseOrDispose { }
    }

    CupertinoCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (exempt) {
                Text("Background", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.secondaryLabel)
                StatusChip(text = "Always running", color = AppTheme.colors.success)
            } else {
                Text("Keep Shield running", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Let Shield run in the background so location and app limits keep " +
                        "working when the screen is off.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.secondaryLabel,
                )
                ActionRow(
                    title = "Unrestricted battery",
                    granted = false,
                    onEnable = { requestBatteryExemption(context) },
                )
            }
            // Autostart lives in OEM settings with no standard API — always offer
            // the shortcut so parents can enable it manually.
            CupertinoListRow(
                title = "Auto-start on boot",
                subtitle = "On some phones (Xiaomi, Oppo, Vivo…) enable this in app settings",
                showDivider = false,
                trailing = {
                    Text(
                        text = "Open",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.colors.accent,
                        modifier = Modifier
                            .clickable { openAppSettings(context) }
                            .padding(horizontal = Spacing.sm, vertical = 4.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun ActionRow(title: String, granted: Boolean, onEnable: () -> Unit) {
    CupertinoListRow(
        title = title,
        showDivider = false,
        trailing = {
            if (granted) {
                StatusChip(text = "On", color = AppTheme.colors.success)
            } else {
                Text(
                    text = "Allow",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppTheme.colors.accent,
                    modifier = Modifier
                        .clickable(onClick = onEnable)
                        .padding(horizontal = Spacing.sm, vertical = 4.dp),
                )
            }
        },
    )
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@SuppressLint("BatteryLife")
private fun requestBatteryExemption(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }.onFailure { openAppSettings(context) }
}

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
