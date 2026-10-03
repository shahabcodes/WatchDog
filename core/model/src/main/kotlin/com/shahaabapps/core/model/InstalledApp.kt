package com.shahaabapps.core.model

/**
 * A launchable app installed on the child device, reported to the parent so
 * they can choose which apps to limit.
 */
data class InstalledApp(
    val packageName: String = "",
    val label: String = "",
)
