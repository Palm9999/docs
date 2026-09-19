package com.sitorplay.app.data.repository

import com.sitorplay.app.domain.model.Player
import kotlinx.coroutines.flow.Flow

interface PlayerRepository {
    fun observeRoster(teamId: Long): Flow<List<Player>>
    fun observePlayer(id: Long): Flow<Player?>
    suspend fun addPlayer(player: Player)
    suspend fun updatePlayer(player: Player)
    suspend fun removePlayer(player: Player)
    suspend fun rosteredExternalIds(teamId: Long): Set<String>
    suspend fun seedIfEmpty(teamId: Long)
}
