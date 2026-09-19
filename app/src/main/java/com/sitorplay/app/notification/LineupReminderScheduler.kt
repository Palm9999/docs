package com.sitorplay.app.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

private const val UNIQUE_WORK_NAME = "lineup_lock_reminder"
private val REMINDER_TIME: LocalTime = LocalTime.of(11, 0)

/** Schedules a weekly reminder for Sunday morning — well before the usual 1pm ET kickoff wave. */
object LineupReminderScheduler {

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<LineupReminderWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(millisUntilNextSunday(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun millisUntilNextSunday(): Long {
        val now = LocalDateTime.now(ZoneId.systemDefault())
        var target = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).with(REMINDER_TIME)
        if (target.isBefore(now)) {
            target = target.plusWeeks(1)
        }
        return Duration.between(now, target).toMillis()
    }
}
