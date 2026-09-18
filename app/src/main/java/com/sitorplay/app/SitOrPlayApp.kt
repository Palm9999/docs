package com.sitorplay.app

import android.app.Application
import com.sitorplay.app.data.repository.PlayerRepository
import com.sitorplay.app.data.sync.NflDataRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SitOrPlayApp : Application() {

    @Inject lateinit var playerRepository: PlayerRepository
    @Inject lateinit var nflDataRepository: NflDataRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            playerRepository.seedIfEmpty()
        }
        applicationScope.launch {
            // Best-effort warm-up so the first player search isn't stuck waiting
            // on a ~14k-entry download. Failures here are fine — search will
            // just retry the fetch itself when the user actually needs it.
            runCatching { nflDataRepository.refreshDirectoryIfStale() }
        }
    }
}
