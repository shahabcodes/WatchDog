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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shahaabapps.core.ui.theme.AppTheme

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
    val image = remember(base64) {
        base64?.let {
            runCatching {
                val bytes = Base64.decode(it, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    if (image != null) {
        Image(
            bitmap = image,
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
