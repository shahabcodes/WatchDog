package com.shahaabapps.core.model

/**
 * How aggressively the child samples location, chosen by the parent.
 *
 * - [SAVER]: balanced-power always — lightest on battery, coarser path.
 * - [ADAPTIVE]: balanced while still, high-accuracy while moving — accurate
 *   trips without the idle-battery cost (the default).
 * - [HIGH]: high-accuracy always — most precise, most battery.
 */
enum class TrackingMode {
    SAVER,
    ADAPTIVE,
    HIGH,
    ;

    companion object {
        val DEFAULT = ADAPTIVE
        fun from(name: String?): TrackingMode = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
