package com.shahaabapps.shield.enforcement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
 * Shows the special-access permissions the enforcement engine needs, re-checking
 * each time the screen resumes (e.g. after returning from system settings).
 */
@Composable
fun EnforcementSetupCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var usageGranted by remember { mutableStateOf(EnforcementPermissions.hasUsageAccess(context)) }
    var accessibilityGranted by remember {
        mutableStateOf(EnforcementPermissions.isAccessibilityEnabled(context))
    }

    LifecycleResumeEffect(Unit) {
        usageGranted = EnforcementPermissions.hasUsageAccess(context)
        accessibilityGranted = EnforcementPermissions.isAccessibilityEnabled(context)
        onPauseOrDispose { }
    }

    CupertinoCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (usageGranted && accessibilityGranted) {
                Text("Protection", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.secondaryLabel)
                StatusChip(text = "Active", color = AppTheme.colors.success)
            } else {
                Text(
                    text = "Finish setup",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Grant these so Watch Dog can apply your limits.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.secondaryLabel,
                )

                PermissionRow(
                    title = "Usage access",
                    granted = usageGranted,
                    onEnable = { context.startActivity(EnforcementPermissions.usageAccessIntent()) },
                )
                PermissionRow(
                    title = "Accessibility (Shield)",
                    granted = accessibilityGranted,
                    onEnable = { context.startActivity(EnforcementPermissions.accessibilityIntent()) },
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
