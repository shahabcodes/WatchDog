package com.shahaabapps.watchdog.feature.screenshot

import android.content.Context
import java.io.File

/** One screenshot stored locally on the parent device. */
data class LocalShot(val file: File, val capturedAtMillis: Long)

/**
 * Keeps downloaded screenshots on the parent device only, under
 * `filesDir/screenshots/{parentUid}/{childId}`, so nothing is retained in the
 * backend. Namespacing by parent account means that if two parent accounts share
 * one physical device, each only ever sees its own downloads — a sign-out leaves
 * no screenshots visible to the next account, and nothing is deleted (these local
 * files are the only copy once the server copy was cleared on download).
 * The filename encodes the capture time.
 */
object LocalScreenshotStore {

    private fun root(context: Context): File = File(context.filesDir, "screenshots")

    private fun dir(context: Context, parentUid: String, childId: String): File =
        File(File(root(context), parentUid), childId).apply { mkdirs() }

    /**
     * One-time move of screenshots saved under the old, account-agnostic layout
     * (`screenshots/{childId}`) into the current account's namespace. Best-effort;
     * preserves the signed-in parent's existing gallery across the upgrade.
     */
    fun migrateLegacyLayout(context: Context, parentUid: String, childId: String) {
        val legacy = File(root(context), childId)
        // A real legacy child dir contains image files; skip anything that is a
        // parentUid namespace dir (its children are child dirs, not images).
        val looksLegacy = legacy.isDirectory &&
            legacy.listFiles()?.any { it.extension == "jpg" || it.extension == "png" } == true
        if (!looksLegacy) return
        val target = dir(context, parentUid, childId)
        legacy.listFiles()?.forEach { file ->
            runCatching { file.copyTo(File(target, file.name), overwrite = false) }
            runCatching { file.delete() }
        }
        runCatching { legacy.delete() }
    }

    fun hasShot(context: Context, parentUid: String, childId: String, capturedAtMillis: Long): Boolean {
        val d = dir(context, parentUid, childId)
        return File(d, "$capturedAtMillis.jpg").exists() || File(d, "$capturedAtMillis.png").exists()
    }

    fun save(context: Context, parentUid: String, childId: String, capturedAtMillis: Long, bytes: ByteArray): File {
        val file = File(dir(context, parentUid, childId), "$capturedAtMillis.jpg")
        file.writeBytes(bytes)
        return file
    }

    fun list(context: Context, parentUid: String, childId: String): List<LocalShot> =
        dir(context, parentUid, childId).listFiles()
            ?.filter { it.extension == "jpg" || it.extension == "png" }
            ?.map { LocalShot(it, it.nameWithoutExtension.toLongOrNull() ?: 0L) }
            ?.sortedByDescending { it.capturedAtMillis }
            ?: emptyList()

    fun delete(shot: LocalShot): Boolean = runCatching { shot.file.delete() }.getOrDefault(false)

    /** Deletes locally stored screenshots older than [maxAgeMillis]. */
    fun pruneOlderThan(context: Context, parentUid: String, childId: String, maxAgeMillis: Long) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        dir(context, parentUid, childId).listFiles()?.forEach { file ->
            val millis = file.nameWithoutExtension.toLongOrNull() ?: return@forEach
            if (millis in 1 until cutoff) runCatching { file.delete() }
        }
    }
}
