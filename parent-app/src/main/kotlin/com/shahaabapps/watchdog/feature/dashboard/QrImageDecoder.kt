package com.shahaabapps.watchdog.feature.dashboard

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * Decodes a QR code from an image the user picked from storage, so pairing works
 * even when the two devices can't be physically together (e.g. a screenshot of
 * the child's code).
 */
object QrImageDecoder {

    fun decodeFromUri(context: Context, uri: Uri): String? {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return null

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)
        val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }

        // Try normal, then inverted (handles dark-mode screenshots).
        return decode(reader, BinaryBitmap(HybridBinarizer(source)))
            ?: decode(reader, BinaryBitmap(HybridBinarizer(source.invert())))
    }

    private fun decode(reader: MultiFormatReader, binaryBitmap: BinaryBitmap): String? = try {
        reader.decodeWithState(binaryBitmap).text
    } catch (_: Exception) {
        null
    }
}
