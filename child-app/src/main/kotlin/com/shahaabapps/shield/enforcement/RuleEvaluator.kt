package com.shahaabapps.shield.enforcement

import com.shahaabapps.core.model.AppRule
import java.util.Calendar

/**
 * Pure decision logic for whether an app should currently be blocked.
 */
object RuleEvaluator {

    data class Decision(val block: Boolean, val reason: String?)

    private val ALLOWED = Decision(block = false, reason = null)

    fun evaluate(
        rule: AppRule?,
        usedMinutesToday: Int,
        now: Calendar = Calendar.getInstance(),
    ): Decision {
        if (rule == null) return ALLOWED

        if (rule.blocked) return Decision(true, "This app is blocked")

        if (rule.schedule.isNotEmpty()) {
            val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val isoDayOfWeek = toIsoDayOfWeek(now.get(Calendar.DAY_OF_WEEK))
            val insideAnyWindow = rule.schedule.any { window ->
                isoDayOfWeek in window.daysOfWeek &&
                    minuteOfDay >= window.startMinuteOfDay &&
                    minuteOfDay < window.endMinuteOfDay
            }
            if (!insideAnyWindow) return Decision(true, "Outside allowed hours")
        }

        val limit = rule.dailyLimitMinutes
        if (limit != null && usedMinutesToday >= limit) {
            return Decision(true, "Daily time limit reached")
        }

        return ALLOWED
    }

    /** Calendar.SUNDAY=1..SATURDAY=7 -> ISO Monday=1..Sunday=7. */
    private fun toIsoDayOfWeek(calendarDayOfWeek: Int): Int =
        when (calendarDayOfWeek) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7 // Sunday
        }
}
