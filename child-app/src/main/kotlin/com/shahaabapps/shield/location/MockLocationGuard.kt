package com.shahaabapps.shield.location

import android.content.Context
import android.location.Location
import android.os.Build
import android.os.SystemClock

/**
 * Fake-GPS detection. Mock-location apps (enabled via Developer options) can feed a
 * believable fake route. Android marks such fixes; Shield drops them and reports
 * it, and the parent is alerted (the "gpsGenuine" guard flag goes false).
 */
object MockLocationGuard {
    private const val PREFS = "mock_location"
    private const val KEY_LAST_MOCK = "last_mock_wall"
    /** Stay flagged this long after the last fake fix. */
    private const val FLAG_WINDOW_MS = 30 * 60 * 1000L

    @Volatile private var lastMockElapsed = 0L

    /** True when [loc] came from a mock-location provider. */
    fun isMock(loc: Location): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) loc.isMock
        else @Suppress("DEPRECATION") loc.isFromMockProvider

    /** Checks [loc]; records a detection. Returns true when it must be ignored. */
    fun reject(context: Context, loc: Location): Boolean {
        if (!isMock(loc)) return false
        lastMockElapsed = SystemClock.elapsedRealtime()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_MOCK, com.shahaabapps.shield.enforcement.TrustedClock.now()).apply()
        return true
    }

    /** False while fake locations were seen in the last 30 minutes. */
    fun gpsGenuine(context: Context): Boolean {
        if (lastMockElapsed > 0L && SystemClock.elapsedRealtime() - lastMockElapsed < FLAG_WINDOW_MS) return false
        val last = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_MOCK, 0L)
        return last == 0L || com.shahaabapps.shield.enforcement.TrustedClock.now() - last > FLAG_WINDOW_MS
    }
}
