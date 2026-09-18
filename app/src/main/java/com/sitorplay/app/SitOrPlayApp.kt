package com.sitorplay.app

import android.app.Application
import com.sitorplay.app.data.repository.PlayerRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SitOrPlayApp : Application() {

    @Inject lateinit var playerRepository: PlayerRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            playerRepository.seedIfEmpty()
        }
    }
}
