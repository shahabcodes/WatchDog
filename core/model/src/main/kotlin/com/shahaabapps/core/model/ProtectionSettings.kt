package com.shahaabapps.core.model

/** Minimum length of the uninstall removal PIN. */
const val PIN_MIN_LEN = 15

/** Maximum length of the uninstall removal PIN. */
const val PIN_MAX_LEN = 20

/**
 * Parent-controlled anti-tamper settings for the child device.
 *
 * [uninstallBlocked] turns on device-admin + accessibility uninstall protection
 * on Shield. The removal PIN (a [PIN_MIN_LEN]–[PIN_MAX_LEN] digit code set by the
 * parent in Watch Dog) is stored only as a salted PBKDF2 hash ([pinSalt] /
 * [pinHash]) — never in plaintext. [legacyPin] carries a plaintext PIN from docs
 * written before hashing existed, for backward-compatible verification only; it
 * is never written back.
 */
data class ProtectionSettings(
    val uninstallBlocked: Boolean = false,
    val pinSalt: String = "",
    val pinHash: String = "",
    val legacyPin: String = "",
    /** When true, newly installed apps are auto-blocked until the parent approves. */
    val blockNewApps: Boolean = false,
) {
    /** Whether a removal PIN has been set (hashed, or a legacy plaintext one). */
    val hasPin: Boolean get() = pinHash.isNotEmpty() || legacyPin.isNotEmpty()
}

/** An app installed on the child device that was auto-blocked, awaiting parent review. */
data class PendingApp(
    val packageName: String = "",
    val label: String = "",
    val installedAtMillis: Long = 0L,
)
