package com.fms.parentalcontrol.core.model

/**
 * Represents a child account as seen from the parent app, including live device state.
 */
data class ChildLink(
    val childId: String = "",
    val profile: UserProfile = UserProfile(role = UserRole.CHILD),
    val device: DeviceInfo = DeviceInfo(),
)

/**
 * Snapshot of a child device's state, reported by the child app.
 */
data class DeviceInfo(
    val online: Boolean = false,
    val batteryPercent: Int = 0,
    val model: String = "",
    val lastSeenMillis: Long = 0L,
)
