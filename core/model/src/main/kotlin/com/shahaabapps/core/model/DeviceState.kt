package com.shahaabapps.core.model

/** The child device's last-reported location, shown live on the parent's map. */
data class ChildLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Float = 0f,
    val timestampMillis: Long = 0L,
)

/**
 * A "ring my child's device" request authored by the parent. The child plays an
 * alarm and shows a notification until it is snoozed or dismissed.
 *
 * [nonce] changes on every new ring so the child can tell a fresh request from a
 * stale one it has already handled.
 */
data class RingCommand(
    val active: Boolean = false,
    val message: String = "",
    val nonce: String = "",
    val requestedAtMillis: Long = 0L,
)

/**
 * A place where the child stayed for a meaningful amount of time (a "stop" in
 * their travel history). Recorded on-device when the child stays within a small
 * radius for at least a few minutes.
 */
data class TravelStop(
    val id: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val arrivalMillis: Long = 0L,
    val departureMillis: Long = 0L,
) {
    val durationMinutes: Long get() = ((departureMillis - arrivalMillis) / 60_000L).coerceAtLeast(0)
}
