package com.shahaabapps.core.model

/**
 * One recurring "focus" window during which apps are blocked on the child device
 * (e.g. Bedtime or School). [startMinute]/[endMinute] are minutes-of-day; a window
 * may cross midnight (start > end). [days] are ISO day-of-week values (1=Mon..7=Sun).
 */
data class FocusWindow(
    val enabled: Boolean = false,
    val startMinute: Int = 0,
    val endMinute: Int = 0,
    val days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
) {
    fun activeAt(minuteOfDay: Int, isoDayOfWeek: Int): Boolean {
        if (!enabled) return false
        if (isoDayOfWeek !in days) return false
        return if (startMinute <= endMinute) {
            minuteOfDay in startMinute until endMinute
        } else {
            // Crosses midnight.
            minuteOfDay >= startMinute || minuteOfDay < endMinute
        }
    }
}

/** Per-child focus schedules. */
data class FocusSettings(
    val bedtime: FocusWindow = FocusWindow(startMinute = 21 * 60, endMinute = 7 * 60),
    val school: FocusWindow = FocusWindow(startMinute = 8 * 60, endMinute = 15 * 60, days = setOf(1, 2, 3, 4, 5)),
) {
    fun anyActiveAt(minuteOfDay: Int, isoDayOfWeek: Int): Boolean =
        bedtime.activeAt(minuteOfDay, isoDayOfWeek) || school.activeAt(minuteOfDay, isoDayOfWeek)
}
