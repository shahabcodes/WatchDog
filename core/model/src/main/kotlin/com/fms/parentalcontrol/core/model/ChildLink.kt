package com.fms.parentalcontrol.core.model

/**
 * A child account document (`children/{childId}`), seen by both apps.
 *
 * @param childId the child device's anonymous auth uid.
 * @param profile the child's display profile.
 * @param pairingCode short code shown in the child's QR for a parent to scan.
 * @param linkedParents parent uids currently linked to this child.
 * @param device live device state reported by the child app.
 */
data class ChildAccount(
    val childId: String = "",
    val profile: UserProfile = UserProfile(role = UserRole.CHILD),
    val pairingCode: String = "",
    val linkedParents: List<String> = emptyList(),
    val device: DeviceInfo = DeviceInfo(),
) {
    val isLinked: Boolean get() = linkedParents.isNotEmpty()
}

/**
 * Snapshot of a child device's state, reported by the child app.
 */
data class DeviceInfo(
    val online: Boolean = false,
    val batteryPercent: Int = 0,
    val model: String = "",
    val lastSeenMillis: Long = 0L,
)

/**
 * Data encoded in the pairing QR code.
 */
data class PairingData(
    val childId: String,
    val code: String,
)
