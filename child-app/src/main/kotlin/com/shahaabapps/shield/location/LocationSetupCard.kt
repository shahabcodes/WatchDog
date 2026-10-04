package com.shahaabapps.shield.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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

@Composable
fun LocationSetupCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var fineGranted by remember { mutableStateOf(hasFineLocation(context)) }
    var backgroundGranted by remember { mutableStateOf(hasBackgroundLocation(context)) }

    val fineLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        fineGranted = hasFineLocation(context)
        if (fineGranted) GeofenceSyncService.start(context)
    }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { backgroundGranted = hasBackgroundLocation(context) }

    // Ask for location automatically the first time setup is shown.
    LaunchedEffect(Unit) {
        if (!fineGranted) {
            fineLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    LifecycleResumeEffect(Unit) {
        fineGranted = hasFineLocation(context)
        backgroundGranted = hasBackgroundLocation(context)
        if (fineGranted) GeofenceSyncService.start(context)
        onPauseOrDispose { }
    }

    CupertinoCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (fineGranted && backgroundGranted) {
                Text("Location", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.secondaryLabel)
                StatusChip(text = "Places active", color = AppTheme.colors.success)
            } else {
                Text("Enable places", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Allow location so parents get alerts for the places they set.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.secondaryLabel,
                )
                PermissionRow(
                    title = "Location access",
                    granted = fineGranted,
                    onEnable = {
                        fineLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                )
                PermissionRow(
                    title = "Allow all the time",
                    granted = backgroundGranted,
                    onEnable = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
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
    )
}

private fun hasFineLocation(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun hasBackgroundLocation(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
