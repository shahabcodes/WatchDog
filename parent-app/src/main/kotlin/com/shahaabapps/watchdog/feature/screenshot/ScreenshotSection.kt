package com.shahaabapps.watchdog.feature.screenshot

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.Spacing

/**
 * Dashboard "Screen" control: request a screenshot of the child's screen and browse
 * the ones already pulled down (stored on this device only). Capture runs silently
 * on the child via the accessibility screenshot API.
 */
@Composable
fun ScreenshotSection(
    modifier: Modifier = Modifier,
    viewModel: ScreenshotViewModel = hiltViewModel(),
) {
    val shots by viewModel.shots.collectAsStateWithLifecycle()
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var requestedAt by remember { mutableStateOf(0L) }
    val waiting = requestedAt > 0 && System.currentTimeMillis() - requestedAt < 25_000
    val listState = rememberLazyListState()

    // Clear the "waiting" hint once a newer screenshot has landed locally, and
    // scroll the gallery to the newest (index 0) so it's visible after capture.
    LaunchedEffect(shots.firstOrNull()?.capturedAtMillis) {
        if ((shots.firstOrNull()?.capturedAtMillis ?: 0L) >= requestedAt) requestedAt = 0L
        if (shots.isNotEmpty()) runCatching { listState.animateScrollToItem(0) }
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
                    Text(
                        if (waiting) "Requested — waiting for the device…" else "Capture what's on the screen now",
                        style = MaterialTheme.typography.bodyMedium,
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
                    "No screenshots yet. Tap Capture to request one — it needs the child device online.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.secondaryLabel,
                )
            } else {
                LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    itemsIndexed(shots, key = { _, it -> it.capturedAtMillis }) { index, shot ->
                        Thumbnail(shot = shot, onClick = { viewerIndex = index })
                    }
                }
            }
        }
    }

    viewerIndex?.let { start ->
        ScreenshotViewer(
            shots = shots,
            startIndex = start,
            onClose = { viewerIndex = null },
            onDelete = { viewModel.delete(it) },
        )
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
private fun Thumbnail(shot: LocalShot, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(210.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppTheme.colors.separator.copy(alpha = 0.3f))
                .clickable(onClick = onClick),
        ) {
            AsyncImage(
                model = shot.file,
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

/** Full-screen, swipeable viewer with zoom, share and delete. */
@Composable
private fun ScreenshotViewer(
    shots: List<LocalShot>,
    startIndex: Int,
    onClose: () -> Unit,
    onDelete: (LocalShot) -> Unit,
) {
    if (shots.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, shots.lastIndex),
        pageCount = { shots.size },
    )

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                ZoomableImage(shot = shots[page])
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
                Text(
                    "${(pagerState.currentPage + 1).coerceAtMost(shots.size)} / ${shots.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                val current = shots.getOrNull(pagerState.currentPage)
                IconButton(onClick = { current?.let { saveToGallery(context, it) } }) {
                    Icon(Icons.Filled.SaveAlt, contentDescription = "Save to gallery", tint = Color.White)
                }
                IconButton(onClick = { current?.let { shareLocalShot(context, it) } }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White)
                }
                IconButton(onClick = { current?.let { onDelete(it) } }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(shot: LocalShot) {
    var zoomed by remember(shot.file.path) { mutableStateOf(false) }
    val target = if (zoomed) 2.5f else 1f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(shot.file.path) {
                detectTapGestures(onDoubleTap = { zoomed = !zoomed })
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = shot.file,
            contentDescription = "Screenshot",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = target, scaleY = target),
        )
    }
}

private fun mimeFor(shot: LocalShot): String =
    if (shot.file.extension.equals("png", ignoreCase = true)) "image/png" else "image/jpeg"

private fun saveToGallery(context: Context, shot: LocalShot) {
    val ok = runCatching {
        val name = "WatchDog_${shot.capturedAtMillis}.${shot.file.extension.ifBlank { "jpg" }}"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, mimeFor(shot))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(
                    android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_PICTURES + "/WatchDog",
                )
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@runCatching false
        resolver.openOutputStream(uri)?.use { out -> shot.file.inputStream().use { it.copyTo(out) } }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            values.clear()
            values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    }.getOrDefault(false)
    android.widget.Toast.makeText(
        context,
        if (ok) "Saved to gallery" else "Couldn't save to gallery",
        android.widget.Toast.LENGTH_SHORT,
    ).show()
}

private fun shareLocalShot(context: Context, shot: LocalShot) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shot.file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeFor(shot)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share screenshot").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
