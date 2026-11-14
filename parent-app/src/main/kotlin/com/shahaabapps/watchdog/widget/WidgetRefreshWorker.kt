package com.shahaabapps.watchdog.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shahaabapps.core.common.Diag
import java.util.concurrent.TimeUnit

/** Refreshes the home-screen widgets: every 30 minutes, and on demand. */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { WidgetRefresher.refresh(applicationContext) }
            .onFailure { Diag.warn("widget", "refresh failed", it) }
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "widget_refresh"
        private const val NOW = "widget_refresh_now"

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Keeps the 30-minute refresh scheduled (only needed while widgets are placed). */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).setConstraints(online).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }

        /** Refresh soon (a new request or install, a tap on the refresh button). */
        fun refreshNow(context: Context) {
            if (!Widgets.anyPlaced(context)) return
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>().setConstraints(online).build(),
            )
        }
    }
}
