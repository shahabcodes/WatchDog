package com.shahaabapps.watchdog.feature.watch

import android.content.Context
import java.io.File

/**
 * One watched-app capture on this phone: [file] is the image, or null when the app
 * hid its screen (a secure window) at that moment.
 */
data class WatchEntry(
    val packageName: String,
    val capturedAtMillis: Long,
    val file: File?,
)

/**
 * Watched-app screenshots kept on the parent's phone only, under
 * `filesDir/watchshots/{parentUid}/{childId}/{package}/{millis}.jpg` (or
 * `{millis}.hidden` for "the app hid its screen"). Namespaced by parent account,
 * like on-demand screenshots, and pruned after [KEEP_MS].
 */
object LocalWatchStore {

    const val KEEP_MS = 14L * 24 * 60 * 60 * 1000

    private fun childDir(context: Context, parentUid: String, childId: String): File =
        File(File(File(context.filesDir, "watchshots"), parentUid), childId)

    fun has(context: Context, parentUid: String, childId: String, pkg: String, millis: Long): Boolean {
        val dir = File(childDir(context, parentUid, childId), safe(pkg))
        return File(dir, "$millis.jpg").exists() || File(dir, "$millis.hidden").exists()
    }

    fun save(context: Context, parentUid: String, childId: String, pkg: String, millis: Long, image: ByteArray?) {
        val dir = File(childDir(context, parentUid, childId), safe(pkg)).apply { mkdirs() }
        if (image == null) File(dir, "$millis.hidden").writeBytes(ByteArray(0))
        else File(dir, "$millis.jpg").writeBytes(image)
    }

    /** Newest capture time stored for this child (child's clock), 0 when none. */
    fun newestMillis(context: Context, parentUid: String, childId: String): Long =
        list(context, parentUid, childId).maxOfOrNull { it.capturedAtMillis } ?: 0L

    /** All captures for this child, newest first; [pkg] narrows to one app. */
    fun list(context: Context, parentUid: String, childId: String, pkg: String? = null): List<WatchEntry> {
        val root = childDir(context, parentUid, childId)
        val dirs = if (pkg != null) listOf(File(root, safe(pkg))) else root.listFiles()?.filter { it.isDirectory }.orEmpty()
        return dirs.flatMap { dir ->
            dir.listFiles().orEmpty().mapNotNull { f ->
                val millis = f.nameWithoutExtension.toLongOrNull() ?: return@mapNotNull null
                when (f.extension) {
                    "jpg" -> WatchEntry(dir.name, millis, f)
                    "hidden" -> WatchEntry(dir.name, millis, null)
                    else -> null
                }
            }
        }.sortedByDescending { it.capturedAtMillis }
    }

    fun delete(entry: WatchEntry, context: Context, parentUid: String, childId: String) {
        val dir = File(childDir(context, parentUid, childId), safe(entry.packageName))
        runCatching { File(dir, "${entry.capturedAtMillis}.jpg").delete() }
        runCatching { File(dir, "${entry.capturedAtMillis}.hidden").delete() }
    }

    fun prune(context: Context, parentUid: String, childId: String) {
        val cutoff = System.currentTimeMillis() - KEEP_MS
        childDir(context, parentUid, childId).listFiles()?.forEach { dir ->
            dir.listFiles()?.forEach { f ->
                val millis = f.nameWithoutExtension.toLongOrNull() ?: return@forEach
                if (millis < cutoff) runCatching { f.delete() }
            }
        }
    }

    // Package names are already file-safe; this only guards against a crafted one.
    private fun safe(pkg: String): String = pkg.replace(Regex("[^A-Za-z0-9._]"), "_")
}
