package com.shahaabapps.watchdog.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * A clean circular map marker (white ring + themed fill) as a [BitmapDescriptor],
 * for lite-mode maps where Compose markers aren't available. Returns null if the
 * Maps SDK isn't ready yet, so the caller falls back to the default pin.
 *
 * Must be called from inside a `GoogleMap { }` content lambda so the Maps SDK is
 * initialized.
 */
@Composable
fun rememberCircleMarker(
    fill: Color,
    ring: Color = Color.White,
    size: Dp = 22.dp,
): BitmapDescriptor? {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(12)
    val fillArgb = fill.toArgb()
    val ringArgb = ring.toArgb()
    return remember(fillArgb, ringArgb, sizePx) {
        runCatching {
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val c = sizePx / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = ringArgb
            canvas.drawCircle(c, c, c, paint)
            paint.color = fillArgb
            canvas.drawCircle(c, c, c * 0.72f, paint)
            BitmapDescriptorFactory.fromBitmap(bmp)
        }.getOrNull()
    }
}
