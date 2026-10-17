package com.shahaabapps.watchdog.feature.security

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Renders [content] as a QR code image, encoding off the main thread. */
@Composable
fun QrImage(content: String, modifier: Modifier = Modifier, sizePx: Int = 640) {
    val image by produceState<ImageBitmap?>(initialValue = null, content, sizePx) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val hints = mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN to 1,
                )
                val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
                val w = matrix.width
                val h = matrix.height
                val pixels = IntArray(w * h)
                for (y in 0 until h) {
                    val row = y * w
                    for (x in 0 until w) pixels[row + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                    setPixels(pixels, 0, w, 0, 0, w, h)
                }.asImageBitmap()
            }.getOrNull()
        }
    }
    image?.let { Image(bitmap = it, contentDescription = "QR code", modifier = modifier) }
}
