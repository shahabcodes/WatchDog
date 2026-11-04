package com.shahaabapps.core.model

/**
 * An app-usage rule for a single package on the child device.
 *
 * @param packageName the target app's package.
 * @param appLabel human-readable app name.
 * @param dailyLimitMinutes total allowed minutes per day, or null for no total limit.
 * @param schedule allowed time windows; outside these the app is blocked.
 * @param blocked hard block regardless of schedule/limit.
 * @param updatedAtMillis when the parent last changed this rule; a parent-approved
 *   temporary unlock is only honoured while it is newer than this, so re-blocking
 *   an app immediately overrides any lingering approval.
 * @param watchEverySeconds "Supervised": while the app is in use, Shield takes one
 *   screenshot at a random moment in every interval of this many seconds and sends
 *   it (sealed) to the parents. 0 = not watched. Ignored while [blocked].
 */
data class AppRule(
    val packageName: String = "",
    val appLabel: String = "",
    val dailyLimitMinutes: Int? = null,
    val schedule: List<TimeWindow> = emptyList(),
    val blocked: Boolean = false,
    val updatedAtMillis: Long = 0L,
    val watchEverySeconds: Int = 0,
) {
    val watched: Boolean get() = !blocked && watchEverySeconds > 0
}

/**
 * An allowed window within a day, in minutes from midnight (local time).
 *
 * @param startMinuteOfDay inclusive start, 0..1439.
 * @param endMinuteOfDay exclusive end, 1..1440.
 * @param daysOfWeek ISO-8601 day numbers (1 = Monday .. 7 = Sunday) the window applies to.
 */
data class TimeWindow(
    val startMinuteOfDay: Int = 0,
    val endMinuteOfDay: Int = 1440,
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
)
