package com.shahaabapps.shield.enforcement

import android.app.usage.UsageStatsManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Reads per-app foreground time for "today" from [UsageStatsManager].
 * Requires the user to have granted Usage Access.
 */
class UsageTracker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val usageStatsManager: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    fun todayForegroundMinutes(packageName: String): Int {
        val manager = usageStatsManager ?: return 0
        val start = startOfTodayMillis()
        val now = System.currentTimeMillis()
        val stats = runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
        }.getOrNull().orEmpty()

        val totalMillis = stats
            .filter { it.packageName == packageName }
            .sumOf { it.totalTimeInForeground }
        return (totalMillis / 60_000L).toInt()
    }

    /** Today's foreground minutes per package (only those with any usage). */
    fun todayUsageByPackage(): Map<String, Int> {
        val manager = usageStatsManager ?: return emptyMap()
        val start = startOfTodayMillis()
        val now = System.currentTimeMillis()
        val stats = runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
        }.getOrNull().orEmpty()

        val millisByPackage = HashMap<String, Long>()
        for (s in stats) {
            if (s.totalTimeInForeground > 0) {
                millisByPackage[s.packageName] = (millisByPackage[s.packageName] ?: 0L) + s.totalTimeInForeground
            }
        }
        return millisByPackage
            .mapValues { (it.value / 60_000L).toInt() }
            .filterValues { it > 0 }
    }

    /**
     * Start of "today" — the child's home-zone day by trusted time — expressed on
     * the phone's clock (usage stats are stamped with the phone's clock). Using the
     * smallest clock offset seen today means moving the clock forward can't reset
     * the daily limit; a jump can only make usage count more, never less.
     */
    private fun startOfTodayMillis(): Long {
        val dayStart = ChildZone.startOfTodayMillis()
        return dayStart + TrustedClock.minPhoneOffsetForDay(ChildZone.today().toString())
    }
}
