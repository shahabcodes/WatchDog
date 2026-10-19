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
        prefs(context).edit().putLong(KEY_UNLOCK_UNTIL, TrustedClock.now() + UNLOCK_WINDOW_MS).apply()
    }

    fun isUnlocked(context: Context): Boolean =
        TrustedClock.now() < prefs(context).getLong(KEY_UNLOCK_UNTIL, 0L)

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

    fun isUninstallScreen(
        packageName: String,
        className: String?,
        text: String? = null,
        ownLabel: String = "",
    ): Boolean {
        // The installer package handles BOTH install and uninstall, so we must not
        // bounce on the package alone — that would also block legitimate APK
        // installs/updates.
        val inInstaller = packageName in INSTALLER_PACKAGES ||
            packageName.contains("packageinstaller", ignoreCase = true)
        val inSettings = packageName.contains("settings", ignoreCase = true)
        if (!inInstaller && !inSettings) return false
        val looksUninstall = (className != null && className.contains("Uninstall", ignoreCase = true)) ||
            (text != null && text.contains("uninstall", ignoreCase = true))
        if (!looksUninstall) return false
        // Only guard OUR OWN app's uninstall — never block uninstalling other apps
        // (e.g. Watch Dog on the same device). The confirm screen names the app, so
        // require our label to appear; if it can't be read, don't bounce (device
        // admin still blocks Shield's removal).
        if (ownLabel.isBlank() || text == null) return false
        return text.contains(ownLabel, ignoreCase = true)
    }

    /** Settings-type apps (incl. the permission controller that hosts app permissions). */
    fun isSettingsPackage(packageName: String): Boolean =
        packageName.contains("settings", ignoreCase = true) ||
            packageName.contains("permissioncontroller", ignoreCase = true)

    /**
     * A Settings page that would switch Shield off without uninstalling it: its
     * accessibility toggle (or the "Stop Shield?" confirm), Usage Access, or its
     * App info page (Force stop, permissions, storage). Requires Shield's own name
     * on screen plus a sign of one of those pages, so other apps' pages and the
     * rest of Settings stay reachable.
     */
    fun isProtectedSettingsScreen(className: String?, screenText: String, ownLabel: String): Boolean {
        if (ownLabel.isBlank() || screenText.isBlank()) return false
        if (!screenText.contains(ownLabel, ignoreCase = true)) return false
        if (className != null && SETTINGS_CLASS_HINTS.any { className.contains(it, ignoreCase = true) }) return true
        val t = screenText.lowercase()
        val label = ownLabel.lowercase()
        return SETTINGS_TEXT_HINTS.any { t.contains(it) } ||
            t.contains("use $label") || t.contains("stop $label") || t.contains("turn off $label")
    }

    private val SETTINGS_CLASS_HINTS = listOf(
        "Accessibility", "UsageAccess", "AppInfo", "InstalledAppDetails", "AppDetails",
        "AppPermission", "AppStorage",
    )
    private val SETTINGS_TEXT_HINTS = listOf(
        "force stop", "usage access", "usage data access", "permit usage", "accessibility",
        "app info", "clear storage", "clear data", "uninstall", "permissions",
    )

    /**
     * The "deactivate device admin" screen. Stock Android uses DeviceAdminAdd, but
     * OEM Settings host it under other classes (DeviceAdminSettings, SubSettings…),
     * so we match any Settings-package screen whose class or on-screen text points
     * at device admin / deactivation. Over-matching the device-admin area is fine —
     * a child shouldn't be managing device admins while protection is on.
     */
    fun isAdminRemovalScreen(packageName: String, className: String?, text: String? = null): Boolean {
        if (!packageName.contains("settings", ignoreCase = true)) return false
        if (className != null && className.contains("DeviceAdmin", ignoreCase = true)) return true
        if (text != null && (
                text.contains("device admin", ignoreCase = true) ||
                    text.contains("deactivate", ignoreCase = true)
                )
        ) {
            return true
        }
        return false
    }
}
