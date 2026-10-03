package com.shahaabapps.shield.usage

import android.content.Context
import android.content.Intent
import com.shahaabapps.core.model.InstalledApp
import dagger.hilt.android.qualifiers.ApplicationContext
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
                InstalledApp(packageName = pkg, label = info.loadLabel(pm).toString())
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
