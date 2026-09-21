package com.sitorplay.app.data.prediction

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Keeps the weekly feature bundle current in the background.
 *
 * Twice a day rather than weekly: the bundle is regenerated whenever the
 * pipeline runs, and the moment that matters is Sunday morning when inactives
 * land, so a weekly schedule anchored to the wrong day would routinely be a week
 * stale. The file is ~64KB, so checking often costs nothing.
 */
class ModelRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun predictionRepository(): PredictionRepository
    }

    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors
            .fromApplication(applicationContext, Dependencies::class.java)
            .predictionRepository()
        // A failure here is almost always a missing URL or no connectivity, so
        // retry rather than fail: the previous bundle stays usable meanwhile.
        return if (repository.refreshWeeklyBundle()) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "model_bundle_refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ModelRefreshWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
