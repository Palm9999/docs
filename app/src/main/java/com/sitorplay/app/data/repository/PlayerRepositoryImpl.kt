package com.sitorplay.app.data.repository

import com.sitorplay.app.data.local.PlayerDao
import com.sitorplay.app.data.local.toDomain
import com.sitorplay.app.data.local.toEntity
import com.sitorplay.app.data.seed.SeedPlayers
import com.sitorplay.app.domain.model.Player
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerRepositoryImpl @Inject constructor(
    private val playerDao: PlayerDao
) : PlayerRepository {

    override fun observeRoster(): Flow<List<Player>> =
        playerDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun addPlayer(player: Player) {
        playerDao.insert(player.toEntity())
    }

    override suspend fun updatePlayer(player: Player) {
        playerDao.update(player.toEntity())
    }

    override suspend fun removePlayer(player: Player) {
        playerDao.delete(player.toEntity())
    }

    override suspend fun seedIfEmpty() {
        if (playerDao.count() == 0) {
            playerDao.insertAll(SeedPlayers.sampleRoster().map { it.toEntity() })
        }
    }
}
