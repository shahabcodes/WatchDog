package com.shahaabapps.core.model

/** Minimum length of the uninstall removal PIN. */
const val PIN_MIN_LEN = 15

/** Maximum length of the uninstall removal PIN. */
const val PIN_MAX_LEN = 20

/**
 * Parent-controlled anti-tamper settings for the child device.
 *
 * [uninstallBlocked] turns on device-admin + accessibility uninstall protection
 * on Shield; [pin] is the numeric code ([PIN_MIN_LEN]–[PIN_MAX_LEN] digits, set by
 * the parent in Watch Dog) that a parent enters on the child device to temporarily
 * allow removal.
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
