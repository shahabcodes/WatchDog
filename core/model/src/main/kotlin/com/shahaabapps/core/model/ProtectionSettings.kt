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
    /**
     * When true, Shield keeps the child out of the Settings pages that switch it
     * off (its accessibility toggle, Usage Access, App info). Independent of
     * [uninstallBlocked]; if a parent never chose, it follows [uninstallBlocked].
     */
    val lockSettings: Boolean = false,
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

/**
 * Which alerts the parents get for a child (`settings/alerts`). Every alert is on
 * unless a parent switched it off; the Cloud Functions check these before pushing.
 */
data class AlertSettings(
    /** App blocking, usage access or uninstall protection switched off on the phone. */
    val protectionOff: Boolean = true,
    /** The phone's date or time was changed by hand. */
    val clockChanged: Boolean = true,
    /** A fake-location (mock GPS) app is being used. */
    val fakeGps: Boolean = true,
    /** The phone has been on another time zone for 2 hours (travel prompt). */
    val travel: Boolean = true,
    /** The phone hasn't checked in for 3 hours (and when it's back). */
    val noCheckIn: Boolean = true,
)
