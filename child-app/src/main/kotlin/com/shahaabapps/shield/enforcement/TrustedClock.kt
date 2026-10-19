package com.shahaabapps.shield.enforcement

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

/**
 * The time Shield enforces by — one the child can't change from Settings.
 *
 * Time rules (bedtime, schedules, daily limits, approved grants) used to read the
 * phone's clock, so moving the clock or time zone bent them, and an approved
 * 15-minute unlock plus a clock rollback stayed unlocked for good. Sources, best
 * first:
 *  1. Android's network time (API 33+), which the user can't set.
 *  2. Server time (HTTPS Date header) anchored to [SystemClock.elapsedRealtime],
 *     which only counts real elapsed time — valid until the next reboot.
 *  3. Offline after a reboot: the phone's clock, but never earlier than the latest
 *     trusted time already seen (so rolling the clock back gains nothing).
 */
object TrustedClock {
    private const val PREFS = "trusted_clock"
    private const val KEY_SERVER = "anchor_server"
    private const val KEY_ELAPSED = "anchor_elapsed"
    private const val KEY_BOOT = "anchor_boot"
    private const val KEY_FLOOR = "floor"
    private const val KEY_OFFSET_DAY = "offset_day"
    private const val KEY_MIN_OFFSET = "min_offset"
    private const val TIME_URL = "https://www.gstatic.com/generate_204"
    private const val RESYNC_MS = 6 * 60 * 60 * 1000L
    private const val CLOCK_CHANGED_MS = 5 * 60 * 1000L

    @Volatile private var appContext: Context? = null
    @Volatile private var anchorServer = 0L
    @Volatile private var anchorElapsed = 0L
    @Volatile private var floor = 0L
    @Volatile private var lastFloorWrite = 0L

    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        val p = prefs(ctx)
        floor = p.getLong(KEY_FLOOR, 0L)
        if (p.getInt(KEY_BOOT, -1) == bootCount(ctx)) {
            anchorServer = p.getLong(KEY_SERVER, 0L)
            anchorElapsed = p.getLong(KEY_ELAPSED, 0L)
        }
    }

    /** Trusted "now" in epoch millis. */
    fun now(): Long {
        val trusted = trustedOrNull()
        if (trusted != null) {
            remember(trusted)
            return trusted
        }
        return maxOf(System.currentTimeMillis(), floor)
    }

    /** True when [now] comes from network/server time rather than the phone's clock. */
    fun isTrusted(): Boolean = trustedOrNull() != null

    /**
     * True when the phone's own clock is more than 5 minutes off trusted time —
     * i.e. someone set the date/time by hand. Unknown (no trusted source) = false.
     */
    fun clockLooksChanged(): Boolean {
        val trusted = trustedOrNull() ?: return false
        return abs(System.currentTimeMillis() - trusted) > CLOCK_CHANGED_MS
    }

    /**
     * The smallest (phone clock − trusted time) offset seen on the given trusted
     * day. Usage statistics are stamped with the phone's clock, so a trusted day
     * maps to phone-clock times via this offset; using the smallest one seen today
     * means a clock jump can only over-count usage, never reset it.
     */
    fun minPhoneOffsetForDay(dayKey: String): Long {
        val ctx = appContext ?: return 0L
        val p = prefs(ctx)
        val current = System.currentTimeMillis() - now()
        val stored = if (p.getString(KEY_OFFSET_DAY, null) == dayKey) p.getLong(KEY_MIN_OFFSET, current) else current
        val min = minOf(stored, current)
        p.edit().putString(KEY_OFFSET_DAY, dayKey).putLong(KEY_MIN_OFFSET, min).apply()
        return min
    }

    /** Fetch server time if there's no anchor for this boot or it's old. Network; call off the main thread. */
    suspend fun syncIfStale() {
        val ctx = appContext ?: return
        val anchored = anchorServer > 0L
        if (anchored && SystemClock.elapsedRealtime() - anchorElapsed < RESYNC_MS) return
        val server = withContext(Dispatchers.IO) { fetchServerTime() } ?: return
        anchorServer = server
        anchorElapsed = SystemClock.elapsedRealtime()
        prefs(ctx).edit()
            .putLong(KEY_SERVER, anchorServer)
            .putLong(KEY_ELAPSED, anchorElapsed)
            .putInt(KEY_BOOT, bootCount(ctx))
            .apply()
        remember(server)
    }

    private fun trustedOrNull(): Long? {
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching { SystemClock.currentNetworkTimeClock().millis() }.getOrNull()?.let { return it }
        }
        if (anchorServer > 0L) return anchorServer + (SystemClock.elapsedRealtime() - anchorElapsed)
        return null
    }

    private fun remember(trusted: Long) {
        if (trusted <= floor) return
        floor = trusted
        val ctx = appContext ?: return
        val elapsed = SystemClock.elapsedRealtime()
        if (elapsed - lastFloorWrite > 60_000L) {
            lastFloorWrite = elapsed
            prefs(ctx).edit().putLong(KEY_FLOOR, trusted).apply()
        }
    }

    /** Server time from an HTTPS response's Date header (second precision). */
    private fun fetchServerTime(): Long? = runCatching {
        val conn = (URL(TIME_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 10_000
            readTimeout = 10_000
            useCaches = false
        }
        try {
            conn.connect()
            conn.date.takeIf { it > 0L }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun bootCount(ctx: Context): Int =
        runCatching { Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
