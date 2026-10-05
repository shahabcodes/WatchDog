package com.shahaabapps.shield.usage

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Base64
import com.shahaabapps.core.model.InstalledApp
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Lists the launchable (user-facing) apps on the device. Uses the launcher
 * intent query, which is allowed by the <queries> entry in the manifest without
 * the broad QUERY_ALL_PACKAGES permission.
 */
class InstalledAppsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun launchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION", "QueryPermissionsNeeded")
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        return resolveInfos
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                InstalledApp(
                    packageName = pkg,
                    label = info.loadLabel(pm).toString(),
                    icon = runCatching { encodeIcon(info.loadIcon(pm)) }.getOrNull(),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Renders an app icon to a small Base64 WebP for the parent to display. */
    private fun encodeIcon(drawable: Drawable, sizePx: Int = 64): String {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        val stream = ByteArrayOutputStream()
        @Suppress("DEPRECATION")
        val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
        bitmap.compress(format, 80, stream)
        bitmap.recycle()
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
