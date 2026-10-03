package com.shahaabapps.shield.screenshot

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.CupertinoListRow
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

@Composable
fun ScreenshotSetupCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenCaptureService.start(context, result.resultCode, data)
        }
    }

    CupertinoCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Screen sharing", style = MaterialTheme.typography.titleMedium)
            Text(
                "Let a parent request a screenshot of this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.secondaryLabel,
            )
            CupertinoListRow(
                title = "Allow screen sharing",
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
