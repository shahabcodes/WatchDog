package com.shahaabapps.shield.enforcement

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The time zone schedules follow: the child's HOME zone, not whatever the phone
 * is set to (changing the phone's zone used to skip bedtime). The parent sets it
 * (settings/timezone); until then it's the phone's zone when Shield first ran.
 * When the child really travels, the parent is prompted to switch it.
 */
object ChildZone {
    private const val PREFS = "child_zone"
    private const val KEY_HOME = "home_first_seen"
    private const val KEY_PARENT = "parent_zone"

    @Volatile private var appContext: Context? = null
    @Volatile private var parentZone: String? = null

    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getString(KEY_HOME, null) == null) {
            p.edit().putString(KEY_HOME, ZoneId.systemDefault().id).apply()
        }
        parentZone = p.getString(KEY_PARENT, null)
    }

    /** The parent's chosen zone (null = not set yet); kept for offline use. */
    fun setParentZone(zoneId: String?) {
        val valid = zoneId?.takeIf { runCatching { ZoneId.of(it) }.isSuccess }
        parentZone = valid
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString(KEY_PARENT, valid)?.apply()
    }

    /** The zone schedules are evaluated in. */
    fun zone(): ZoneId {
        parentZone?.let { id -> runCatching { ZoneId.of(id) }.getOrNull()?.let { return it } }
        val first = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY_HOME, null)
        return first?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
    }

    /** Trusted "now" in the child's home zone. */
    fun now(): ZonedDateTime = Instant.ofEpochMilli(TrustedClock.now()).atZone(zone())

    /** Today's date (home zone, trusted time), e.g. for usage day keys. */
    fun today(): LocalDate = now().toLocalDate()

    /** Start of today in the home zone, as trusted epoch millis. */
    fun startOfTodayMillis(): Long = today().atStartOfDay(zone()).toInstant().toEpochMilli()
}
