package com.shahaabapps.shield.location

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
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.device.DeviceRepository
import com.shahaabapps.core.model.TrailPoint
import com.shahaabapps.shield.data.PendingTrailDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Drains the on-device [PendingTrailDao] queue to Firestore. Runs only when the
 * network is connected (WorkManager constraint), so capture never blocks on the
 * network — points are buffered locally and uploaded here, oldest first, then
 * deleted. Retries (with backoff) if an upload fails or more remain.
 */
class LocationFlushWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun deviceRepository(): DeviceRepository
        fun authRepository(): AuthRepository
        fun pendingTrailDao(): PendingTrailDao
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val dao = deps.pendingTrailDao()
        val batch = runCatching { dao.oldest(BATCH) }.getOrNull() ?: return Result.retry()
        if (batch.isEmpty()) return Result.success()

        // Each point belongs to one child; group by (child, local day) for one write.
        val byKey = batch.groupBy { it.childId to dayOf(it.timestampMillis) }
        for ((key, pts) in byKey) {
            val (childId, date) = key
            val result = deps.deviceRepository().appendTrailPoints(
                childId,
                date,
                pts.map { TrailPoint(it.lat, it.lng, it.timestampMillis) },
            )
            when (result) {
                is AppResult.Success -> runCatching { dao.delete(pts.map { it.id }) }
                is AppResult.Failure -> return Result.retry()
            }
        }
        // More queued than one batch — come back for the rest.
        val remaining = runCatching { dao.count() }.getOrDefault(0)
        return if (remaining > 0) Result.retry() else Result.success()
    }

    private fun dayOf(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    companion object {
        private const val BATCH = 300
        private const val ONE_TIME = "location-flush"
        private const val PERIODIC = "location-flush-periodic"

        private val networkConstraint =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Kick a flush as soon as the network allows (coalesces if already queued). */
        fun flushNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<LocationFlushWorker>()
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.KEEP, request)
        }

        /** Periodic backstop in case a one-off flush was dropped. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<LocationFlushWorker>(30, TimeUnit.MINUTES)
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
