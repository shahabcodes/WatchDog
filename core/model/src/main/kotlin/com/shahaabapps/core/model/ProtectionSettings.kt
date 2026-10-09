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
)
