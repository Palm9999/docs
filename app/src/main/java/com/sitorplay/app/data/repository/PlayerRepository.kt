package com.sitorplay.app.data.repository

import com.sitorplay.app.domain.model.Player
import kotlinx.coroutines.flow.Flow

interface PlayerRepository {
    fun observeRoster(): Flow<List<Player>>
    suspend fun addPlayer(player: Player)
    suspend fun updatePlayer(player: Player)
    suspend fun removePlayer(player: Player)
    suspend fun seedIfEmpty()
}
