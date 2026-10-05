package com.shahaabapps.core.model

/**
 * A launchable app installed on the child device, reported to the parent so
 * they can choose which apps to limit.
 */
data class InstalledApp(
    val packageName: String = "",
    val label: String = "",
    /** The app's launcher icon as a Base64 PNG (small), or null if unavailable. */
    val icon: String? = null,
    /** First-install time on the child device (epoch millis), or 0 if unknown. */
    val installedAtMillis: Long = 0L,
)
