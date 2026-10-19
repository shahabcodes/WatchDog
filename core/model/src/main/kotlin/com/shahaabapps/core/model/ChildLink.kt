package com.shahaabapps.core.model

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
 * Child-reported diagnostic of the location pipeline, so the parent can see the
 * exact reason location is (or isn't) updating rather than a heuristic guess.
 * Stored in plaintext (no coordinates — only states/timestamps/accuracy) so it
 * stays readable even when location *decryption* is unavailable.
 *
 * [state]: "tracking" | "stopped" | "stopped_app_removed" | "no_permission" | "location_off".
 * [lastOutcome]: "published" | "rejected_low_accuracy" | "rejected_teleport" | "write_failed".
 */
data class LocationStatus(
    val state: String = "",
    val lastOutcome: String = "",
    val lastOutcomeMillis: Long = 0L,
    /** Last raw fix the OS delivered to the app. */
    val lastFixMillis: Long = 0L,
    /** Last fix actually written to the parent. */
    val lastPublishMillis: Long = 0L,
    val lastAccuracyMeters: Float = 0f,
    val updatedAtMillis: Long = 0L,
)

/**
 * Snapshot of a child device's state, reported by the child app.
 */
data class DeviceInfo(
    val online: Boolean = false,
    val batteryPercent: Int = 0,
    val model: String = "",
    val lastSeenMillis: Long = 0L,
    /** Whether the device is plugged in / charging. */
    val charging: Boolean = false,
    /** Recent battery drain while discharging, in % per hour (0 when unknown). */
    val batteryDrainPerHour: Double = 0.0,
    /** Latest battery temperature in tenths of °C (e.g. 250 = 25.0 °C); 0 when unknown. */
    val temperatureTenthsC: Int = 0,
    /** The permissions/special-access the child has granted (null if never reported). */
    val permissions: DevicePermissions? = null,
    /** Detailed battery history (hourly levels + last charge), null if never reported. */
    val batteryStats: BatteryStats? = null,
)

/**
 * One battery reading at a point in time.
 *
 * @param tempTenthsC battery temperature in tenths of a degree Celsius (e.g. 250 =
 *   25.0 °C), as reported by the OS; 0 when unknown.
 */
data class BatterySample(val atMillis: Long = 0L, val percent: Int = 0, val tempTenthsC: Int = 0)

/** Hourly battery history and the most recent charge session. */
data class BatteryStats(
    val lastChargeStartMillis: Long = 0L,
    val lastChargeEndMillis: Long = 0L,
    val lastChargeFromPercent: Int = 0,
    val lastChargeToPercent: Int = 0,
    /** Up to the last ~24 hourly readings, oldest first. */
    val hourly: List<BatterySample> = emptyList(),
    /**
     * Temperature history on a finer (~5 min) cadence than [hourly], so a useful
     * recent-temperature curve appears within minutes. Each sample's [BatterySample.tempTenthsC]
     * carries the reading. Up to ~24 samples (~2 h), oldest first.
     */
    val temperatureHistory: List<BatterySample> = emptyList(),
)

/**
 * Snapshot of the special-access permissions Shield needs, reported by the child
 * so the parent can see what's granted and what's blocking features.
 */
data class DevicePermissions(
    /** Fine or coarse location granted at all. */
    val location: Boolean = false,
    /** Background ("Allow all the time") location. */
    val locationAlways: Boolean = false,
    /** Accessibility service enabled (app detection/blocking + screenshots). */
    val accessibility: Boolean = false,
    /** Usage Access (to measure screen time). */
    val usageAccess: Boolean = false,
    /** Shield is an active device admin (uninstall protection). */
    val adminActive: Boolean = false,
    /** Notifications allowed. */
    val notifications: Boolean = false,
    /** Exempt from battery optimization ("Unrestricted"). */
    val batteryUnrestricted: Boolean = false,
    /** Allowed to show full-screen alerts (the "ring device" alarm); user-toggleable on Android 14+. */
    val fullScreenAlarm: Boolean = false,
    /** All-files / storage access — needed for the sideload install guard. */
    val storageAccess: Boolean = false,
    // --- device state that affects whether location can update in the background ---
    /** System battery saver is ON (can delay/stop background location). */
    val powerSaveMode: Boolean = false,
    /** The OS location toggle (GPS) is OFF — no app can get a fix. */
    val locationServicesOff: Boolean = false,
    /** The app's background activity is restricted by the system. */
    val backgroundRestricted: Boolean = false,
    val reportedAtMillis: Long = 0L,
)

/**
 * Data encoded in the pairing QR code. [childPublicKey] (base64 X.509 SPKI of
 * the child device's E2EE key) is carried so the parent obtains it directly
 * from the physical device — an out-of-band channel a compromised backend can't
 * tamper with. Nullable for codes produced before E2EE existed.
 */
data class PairingData(
    val childId: String,
    val code: String,
    val childPublicKey: String? = null,
)
