package com.shahaabapps.shield.location

import android.content.Context
import com.shahaabapps.core.model.BatterySample
import com.shahaabapps.core.model.BatteryStats

/**
 * Tracks battery detail across heartbeats and process restarts (state persisted in
 * SharedPreferences): an hourly level history and the most recent charge session
 * (when it ended and how much it gained). Call [record] on each battery reading.
 */
class BatteryStatsTracker(context: Context) {

    private val prefs = context.getSharedPreferences("battery_stats", Context.MODE_PRIVATE)

    fun record(percent: Int, charging: Boolean, now: Long): BatteryStats {
        val wasCharging = prefs.getBoolean(KEY_WAS_CHARGING, false)
        var chargeStartMs = prefs.getLong(KEY_CHARGE_START_MS, 0L)
        var chargeStartPct = prefs.getInt(KEY_CHARGE_START_PCT, 0)
        var lastStart = prefs.getLong(KEY_LAST_START_MS, 0L)
        var lastEnd = prefs.getLong(KEY_LAST_END_MS, 0L)
        var lastFrom = prefs.getInt(KEY_LAST_FROM, 0)
        var lastTo = prefs.getInt(KEY_LAST_TO, 0)

        if (percent in 1..100) {
            if (charging && !wasCharging) {
                chargeStartMs = now
                chargeStartPct = percent
            } else if (!charging && wasCharging && chargeStartMs > 0L) {
                lastStart = chargeStartMs
                lastEnd = now
                lastFrom = chargeStartPct
                lastTo = percent
                chargeStartMs = 0L
                chargeStartPct = 0
            }
        }

        // Hourly sample: record at most once per clock-hour bucket.
        var hourly = parse(prefs.getString(KEY_HOURLY, "") ?: "")
        val bucket = now - (now % HOUR_MS)
        if (percent in 1..100 && prefs.getLong(KEY_LAST_BUCKET, -1L) != bucket) {
            hourly = (hourly + BatterySample(now, percent)).takeLast(MAX_SAMPLES)
            prefs.edit().putLong(KEY_LAST_BUCKET, bucket).apply()
        }

        prefs.edit()
            .putBoolean(KEY_WAS_CHARGING, charging)
            .putLong(KEY_CHARGE_START_MS, chargeStartMs)
            .putInt(KEY_CHARGE_START_PCT, chargeStartPct)
            .putLong(KEY_LAST_START_MS, lastStart)
            .putLong(KEY_LAST_END_MS, lastEnd)
            .putInt(KEY_LAST_FROM, lastFrom)
            .putInt(KEY_LAST_TO, lastTo)
            .putString(KEY_HOURLY, serialize(hourly))
            .apply()

        return BatteryStats(
            lastChargeStartMillis = lastStart,
            lastChargeEndMillis = lastEnd,
            lastChargeFromPercent = lastFrom,
            lastChargeToPercent = lastTo,
            hourly = hourly,
        )
    }

    private fun serialize(samples: List<BatterySample>): String =
        samples.joinToString(",") { "${it.atMillis}:${it.percent}" }

    private fun parse(s: String): List<BatterySample> =
        if (s.isBlank()) emptyList() else s.split(",").mapNotNull { part ->
            val bits = part.split(":")
            val t = bits.getOrNull(0)?.toLongOrNull()
            val p = bits.getOrNull(1)?.toIntOrNull()
            if (t != null && p != null) BatterySample(t, p) else null
        }

    private companion object {
        const val HOUR_MS = 3_600_000L
        const val MAX_SAMPLES = 24
        const val KEY_WAS_CHARGING = "wasCharging"
        const val KEY_CHARGE_START_MS = "chargeStartMs"
        const val KEY_CHARGE_START_PCT = "chargeStartPct"
        const val KEY_LAST_START_MS = "lastChargeStartMs"
        const val KEY_LAST_END_MS = "lastChargeEndMs"
        const val KEY_LAST_FROM = "lastChargeFrom"
        const val KEY_LAST_TO = "lastChargeTo"
        const val KEY_HOURLY = "hourly"
        const val KEY_LAST_BUCKET = "lastHourBucket"
    }
}
