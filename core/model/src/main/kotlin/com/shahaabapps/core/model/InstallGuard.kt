package com.shahaabapps.core.model

/**
 * Parent policy for sideloaded (unknown-source) app installs/updates, enforced on
 * the child by matching a downloaded APK's TRUE package name (read from its
 * manifest) against [packages].
 *
 * @param enabled whether the guard is active at all.
 * @param allowlist when true, ONLY [packages] may be sideloaded (everything else
 *   is quarantined); when false (default), [packages] are the ones blocked and
 *   everything else is allowed.
 * @param packages package names the policy applies to (e.g. "com.instagram.android").
 */
data class InstallGuard(
    val enabled: Boolean = false,
    val allowlist: Boolean = false,
    val packages: List<String> = emptyList(),
) {
    /** Whether a sideloaded APK with [pkg] should be blocked under this policy. */
    fun blocks(pkg: String): Boolean {
        if (!enabled) return false
        val listed = pkg in packages
        return if (allowlist) !listed else listed
    }
}
