package com.sitorplay.app

import android.app.Application
import com.sitorplay.app.data.prediction.ModelRefreshWorker
import com.sitorplay.app.data.prediction.PredictionRepository
import com.sitorplay.app.data.repository.PlayerRepository
import com.sitorplay.app.data.repository.TeamRepository
import com.sitorplay.app.data.settings.AppSettingsRepository
import com.sitorplay.app.data.sync.NflDataRepository
import com.sitorplay.app.notification.LineupReminderScheduler
import com.sitorplay.app.notification.ensureLineupReminderChannel
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SitOrPlayApp : Application() {

    @Inject lateinit var playerRepository: PlayerRepository
    @Inject lateinit var teamRepository: TeamRepository
    @Inject lateinit var appSettingsRepository: AppSettingsRepository
    @Inject lateinit var nflDataRepository: NflDataRepository
    @Inject lateinit var predictionRepository: PredictionRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureLineupReminderChannel(this)
        if (appSettingsRepository.lockRemindersEnabled.value) {
            // Re-arm on every process start; WorkManager itself already survives reboots,
            // but this also picks up if the unique work was somehow lost.
            LineupReminderScheduler.schedule(this)
        }
        applicationScope.launch {
            val teamId = teamRepository.ensureDefaultTeam()
            appSettingsRepository.ensureSelectedTeam(teamId)
            playerRepository.seedIfEmpty(teamId)
        }
        applicationScope.launch {
            // Parse the model once at startup so the first roster render is not
            // waiting on ~35,000 tree nodes. Also picks up any cached week.
            runCatching { predictionRepository.ensureLoaded() }
            if (appSettingsRepository.modelBundleUrl.value.isNotBlank()) {
                ModelRefreshWorker.schedule(this@SitOrPlayApp)
                runCatching { predictionRepository.refreshWeeklyBundle() }
            }
        }
        applicationScope.launch {
            // Best-effort warm-up so the first player search isn't stuck waiting
            // on a ~14k-entry download. Failures here are fine — search will
            // just retry the fetch itself when the user actually needs it.
            runCatching { nflDataRepository.refreshDirectoryIfStale() }
        }
    }
}
