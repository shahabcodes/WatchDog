package com.shahaabapps.core.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shahaabapps.core.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders an app's icon from a Base64 image (as reported by the child), falling
 * back to a coloured monogram of the app's first letter.
 */
@Composable
fun AppIcon(
    base64: String?,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    // Decode off the main thread so a card scrolling into view doesn't block the
    // frame; the monogram shows until the bitmap is ready.
    val image by produceState<ImageBitmap?>(initialValue = null, base64) {
        value = base64?.let {
            withContext(Dispatchers.Default) {
                runCatching {
                    val bytes = Base64.decode(it, Base64.NO_WRAP)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    val bmp = image
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = modifier.size(size).clip(RoundedCornerShape(10.dp)),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(AppTheme.colors.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.trim().firstOrNull()?.uppercase() ?: "?",
                color = AppTheme.colors.accent,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
