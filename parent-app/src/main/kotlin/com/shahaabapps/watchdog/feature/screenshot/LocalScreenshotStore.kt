package com.shahaabapps.watchdog.feature.screenshot

import android.content.Context
import java.io.File

/** One screenshot stored locally on the parent device. */
data class LocalShot(val file: File, val capturedAtMillis: Long)

/**
 * Keeps downloaded screenshots on the parent device only (filesDir/screenshots/{childId}),
 * so nothing is retained in the backend. The filename encodes the capture time.
 */
object LocalScreenshotStore {

    private fun dir(context: Context, childId: String): File =
        File(context.filesDir, "screenshots/$childId").apply { mkdirs() }

    fun hasShot(context: Context, childId: String, capturedAtMillis: Long): Boolean =
        File(dir(context, childId), "$capturedAtMillis.png").exists()

    fun save(context: Context, childId: String, capturedAtMillis: Long, bytes: ByteArray): File {
        val file = File(dir(context, childId), "$capturedAtMillis.png")
        file.writeBytes(bytes)
        return file
    }

    fun list(context: Context, childId: String): List<LocalShot> =
        dir(context, childId).listFiles()
            ?.filter { it.extension == "png" }
            ?.map { LocalShot(it, it.nameWithoutExtension.toLongOrNull() ?: 0L) }
            ?.sortedByDescending { it.capturedAtMillis }
            ?: emptyList()

    fun delete(shot: LocalShot): Boolean = runCatching { shot.file.delete() }.getOrDefault(false)
}
