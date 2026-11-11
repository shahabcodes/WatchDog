package com.shahaabapps.core.model

/**
 * An app-usage rule for a single package on the child device.
 *
 * @param packageName the target app's package.
 * @param appLabel human-readable app name.
 * @param dailyLimitMinutes total allowed minutes per day, or null for no total limit.
 * @param schedule time windows: the app's allowed hours (outside them it is
 *   blocked), or its blocked hours when [scheduleBlocks].
 * @param scheduleBlocks the [schedule] windows are blocked hours, not allowed hours.
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
    val scheduleBlocks: Boolean = false,
) {
    val watched: Boolean get() = !blocked && watchEverySeconds > 0
}

/**
 * A window within a day, in minutes from midnight (local time). An end before the
 * start runs overnight (22:00 – 07:00 is 22:00 to midnight, then midnight to 07:00).
 *
 * @param startMinuteOfDay inclusive start, 0..1439.
 * @param endMinuteOfDay exclusive end, 1..1440.
 * @param daysOfWeek ISO-8601 day numbers (1 = Monday .. 7 = Sunday) the window applies to.
 */
data class TimeWindow(
    val startMinuteOfDay: Int = 0,
    val endMinuteOfDay: Int = 1440,
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
) {
    /**
     * Whether [minuteOfDay] on ISO day [isoDayOfWeek] falls inside. An overnight
     * window's after-midnight part belongs to the day it started on.
     */
    fun contains(isoDayOfWeek: Int, minuteOfDay: Int): Boolean {
        if (startMinuteOfDay == endMinuteOfDay) return false
        if (startMinuteOfDay < endMinuteOfDay) {
            return isoDayOfWeek in daysOfWeek && minuteOfDay >= startMinuteOfDay && minuteOfDay < endMinuteOfDay
        }
        val previousDay = if (isoDayOfWeek == 1) 7 else isoDayOfWeek - 1
        return (isoDayOfWeek in daysOfWeek && minuteOfDay >= startMinuteOfDay) ||
            (previousDay in daysOfWeek && minuteOfDay < endMinuteOfDay)
    }
}
