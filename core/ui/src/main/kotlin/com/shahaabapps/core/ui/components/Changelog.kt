package com.shahaabapps.core.ui.components

/** One released version and what changed in it. Newest first in [Changelog.entries]. */
data class ReleaseNote(
    val version: String,
    val highlights: List<String>,
)

/**
 * In-app release history shown in the About section. Keep newest first and in
 * sync with CHANGELOG.md at the repo root.
 */
object Changelog {
    val entries: List<ReleaseNote> = listOf(
        ReleaseNote(
            version = "0.3",
            highlights = listOf(
                "Live child location on Watch Dog, Family-Link style",
                "Ring the child's device with a full-screen alarm — stop it with the volume or power button",
                "Guided setup now asks for every permission it needs",
                "Confirmations before deleting places or rules; undo while drawing",
                "Warmer, more colorful look",
            ),
        ),
        ReleaseNote(
            version = "0.2",
            highlights = listOf(
                "Custom map markers and polygon areas",
                "Multiple allowed-hours windows per app",
                "Screen-time usage reports and a geofence activity feed",
            ),
        ),
        ReleaseNote(
            version = "0.1",
            highlights = listOf(
                "QR-code pairing and family profiles",
                "App time limits, blocking and schedules",
                "Location geofences with enter/leave alerts",
            ),
        ),
    )
}
