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

    override fun observeRoster(teamId: Long): Flow<List<Player>> =
        playerDao.observeByTeam(teamId).map { entities -> entities.map { it.toDomain() } }

    override fun observePlayer(id: Long): Flow<Player?> =
        playerDao.observeById(id).map { it?.toDomain() }

    override suspend fun addPlayer(player: Player) {
        playerDao.insert(player.toEntity())
    }

    override suspend fun updatePlayer(player: Player) {
        playerDao.update(player.toEntity())
    }

    override suspend fun removePlayer(player: Player) {
        playerDao.delete(player.toEntity())
    }

    override suspend fun rosteredExternalIds(teamId: Long): Set<String> =
        playerDao.rosteredExternalIds(teamId).toSet()

    override suspend fun seedIfEmpty(teamId: Long) {
        if (playerDao.countForTeam(teamId) == 0) {
            playerDao.insertAll(SeedPlayers.sampleRoster().map { it.copy(teamId = teamId).toEntity() })
        }
    }
}
