package com.shahaabapps.shield.enforcement

import android.content.Context

/**
 * Small synchronous store the accessibility guard reads on every window change:
 * whether uninstall protection is on, and a temporary "unlocked" window after a
 * parent enters the PIN (during which the guard stands down so removal can proceed).
 */
object ProtectionLocalStore {
    private const val PREFS = "shield_protection"
    private const val KEY_BLOCKED = "blocked"
    private const val KEY_UNLOCK_UNTIL = "unlock_until"
    private const val KEY_BLOCK_NEW_APPS = "block_new_apps"

    /** How long a correct PIN keeps the guard down (to allow removal). */
    const val UNLOCK_WINDOW_MS = 2 * 60_000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setBlocked(context: Context, blocked: Boolean) {
        prefs(context).edit().putBoolean(KEY_BLOCKED, blocked).apply()
    }

    fun isBlocked(context: Context): Boolean = prefs(context).getBoolean(KEY_BLOCKED, false)

    fun setBlockNewApps(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_BLOCK_NEW_APPS, value).apply()
    }

    fun isBlockNewApps(context: Context): Boolean = prefs(context).getBoolean(KEY_BLOCK_NEW_APPS, false)

    fun unlock(context: Context) {
        prefs(context).edit().putLong(KEY_UNLOCK_UNTIL, System.currentTimeMillis() + UNLOCK_WINDOW_MS).apply()
    }

    fun isUnlocked(context: Context): Boolean =
        System.currentTimeMillis() < prefs(context).getLong(KEY_UNLOCK_UNTIL, 0L)

    /** Package names / class hints that indicate an uninstall or admin-removal screen. */
    val INSTALLER_PACKAGES = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.oplus.packageinstaller",
        "com.oppo.packageinstaller",
        "com.vivo.packageinstaller",
    )

    fun isUninstallScreen(packageName: String, className: String?): Boolean {
        if (packageName in INSTALLER_PACKAGES) return true
        if (packageName.contains("packageinstaller", ignoreCase = true)) return true
        if (className != null && className.contains("Uninstall", ignoreCase = true)) return true
        return false
    }

    fun isAdminRemovalScreen(packageName: String, className: String?): Boolean =
        packageName == "com.android.settings" &&
            className != null && className.contains("DeviceAdminAdd", ignoreCase = true)
}
