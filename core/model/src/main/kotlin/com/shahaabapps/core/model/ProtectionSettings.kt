package com.shahaabapps.core.model

/**
 * Parent-controlled anti-tamper settings for the child device.
 *
 * [uninstallBlocked] turns on device-admin + accessibility uninstall protection
 * on Shield; [pin] is the 4-digit code (set by the parent in Watch Dog) that a
 * parent enters on the child device to temporarily allow removal.
 */
data class ProtectionSettings(
    val uninstallBlocked: Boolean = false,
    val pin: String = "",
    /** When true, newly installed apps are auto-blocked until the parent approves. */
    val blockNewApps: Boolean = false,
)

/** An app installed on the child device that was auto-blocked, awaiting parent review. */
data class PendingApp(
    val packageName: String = "",
    val label: String = "",
    val installedAtMillis: Long = 0L,
)
