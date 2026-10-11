package com.shahaabapps.watchdog.feature.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.shahaabapps.core.model.Screenshot
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

/**
 * Top-of-dashboard control: request a screenshot of the child's screen and browse
 * the ones that come back. (Capture needs a one-time consent on the child device
 * and shows an on-screen indicator while active — Android requirement.)
 */
@Composable
fun ScreenshotSection(
    modifier: Modifier = Modifier,
    viewModel: ScreenshotViewModel = hiltViewModel(),
) {
    val shots by viewModel.screenshots.collectAsStateWithLifecycle()
    var enlarged by remember { mutableStateOf<Screenshot?>(null) }
    var requestedAt by remember { mutableStateOf(0L) }
    val waiting = requestedAt > 0 && System.currentTimeMillis() - requestedAt < 20_000

    // Clear the "waiting" hint once a newer screenshot arrives.
    LaunchedEffect(shots.firstOrNull()?.id) {
        if ((shots.firstOrNull()?.capturedAtMillis ?: 0L) >= requestedAt) requestedAt = 0L
    }

    CupertinoCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppTheme.colors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = AppTheme.colors.accent)
                }
                Column(modifier = Modifier.weight(1f).padding(start = Spacing.md)) {
                    Text("Screen", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (waiting) "Requested — waiting for the device…" else "Capture what's on the screen now",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.secondaryLabel,
                    )
                }
                CaptureButton(
                    waiting = waiting,
                    onClick = {
                        viewModel.request()
                        requestedAt = System.currentTimeMillis()
                    },
                )
            }

            if (shots.isEmpty()) {
                Text(
                    "No screenshots yet. Tap capture to request one — it needs the child device online with screen capture allowed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.secondaryLabel,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(shots, key = { it.id }) { shot ->
                        Thumbnail(shot = shot, onClick = { enlarged = shot })
                    }
                }
            }
        }
    }

    enlarged?.let { shot ->
        Dialog(onDismissRequest = { enlarged = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .clickable { enlarged = null },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = shot.url,
                    contentDescription = "Screenshot",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.5f),
                )
            }
        }
    }
}

@Composable
private fun CaptureButton(waiting: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(AppTheme.colors.accent)
            .clickable(enabled = !waiting, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        if (waiting) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            Text("Capture", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Thumbnail(shot: Screenshot, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(110.dp)
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppTheme.colors.separator.copy(alpha = 0.3f))
                .clickable(onClick = onClick),
        ) {
            AsyncImage(
                model = shot.url,
                contentDescription = "Screenshot",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            relativeTime(shot.capturedAtMillis),
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.secondaryLabel,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun relativeTime(millis: Long): String {
    if (millis <= 0) return ""
    val mins = (System.currentTimeMillis() - millis) / 60_000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 1440 -> "${mins / 60}h ago"
        else -> "${mins / 1440}d ago"
    }
}
