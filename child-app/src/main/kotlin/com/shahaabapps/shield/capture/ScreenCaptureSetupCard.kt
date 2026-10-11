package com.shahaabapps.shield.capture

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
 * Lets the child grant one-time screen-capture consent so parents can request a
 * screenshot. Android shows a mandatory capture indicator while this is active.
 */
@Composable
fun ScreenCaptureSetupCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var running by remember { mutableStateOf(ScreenCaptureService.running) }

    LifecycleResumeEffect(Unit) {
        running = ScreenCaptureService.running
        onPauseOrDispose { }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenCaptureService.start(context, result.resultCode, data)
            running = true
        }
    }

    CupertinoCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (running) {
                Text("Screen capture", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.secondaryLabel)
                StatusChip(text = "On", color = AppTheme.colors.success)
                CupertinoListRow(
                    title = "Allow parent screenshots",
                    subtitle = "Capture indicator shows while active",
                    showDivider = false,
                    trailing = {
                        Text(
                            text = "Turn off",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppTheme.colors.destructive,
                            modifier = Modifier
                                .clickable { ScreenCaptureService.stop(context); running = false }
                                .padding(horizontal = Spacing.sm, vertical = 4.dp),
                        )
                    },
                )
            } else {
                Text("Allow screen capture", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Let a parent request a screenshot of this phone. Android shows a " +
                        "screen-capture indicator whenever it's active.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.secondaryLabel,
                )
                CupertinoListRow(
                    title = "Screen capture",
                    showDivider = false,
                    trailing = {
                        Text(
                            text = "Enable",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppTheme.colors.accent,
                            modifier = Modifier
                                .clickable {
                                    val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                                        as MediaProjectionManager
                                    launcher.launch(mpm.createScreenCaptureIntent())
                                }
                                .padding(horizontal = Spacing.sm, vertical = 4.dp),
                        )
                    },
                )
            }
        }
    }
}
