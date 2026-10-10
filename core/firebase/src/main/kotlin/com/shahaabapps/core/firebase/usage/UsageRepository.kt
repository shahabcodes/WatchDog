package com.shahaabapps.core.firebase.usage

import com.shahaabapps.core.common.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Per-day, per-app screen time.
 * Firestore: children/{childId}/usage/{yyyy-MM-dd} = { apps: {package: minutes} }
 */
interface UsageRepository {

    /** Child: report today's per-app usage (minutes). */
    suspend fun reportUsage(childId: String, date: String, usageMinutes: Map<String, Int>): AppResult<Unit>

    /** Parent: observe a day's usage (package -> minutes). */
    fun observeUsage(childId: String, date: String): Flow<Map<String, Int>>

    /**
     * Parent: observe total screen-time minutes per day from [sinceDate] (inclusive,
     * yyyy-MM-dd) onward, as a map of date -> total minutes. Days with no data are absent.
     */
    fun observeDailyTotals(childId: String, sinceDate: String): Flow<Map<String, Int>>
}
