package com.sitorplay.app.data.repository

import com.sitorplay.app.data.local.TeamDao
import com.sitorplay.app.data.local.toDomain
import com.sitorplay.app.data.local.toEntity
import com.sitorplay.app.domain.model.Team
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_TEAM_NAME = "My Team"

@Singleton
class TeamRepositoryImpl @Inject constructor(
    private val teamDao: TeamDao
) : TeamRepository {

    override fun observeTeams(): Flow<List<Team>> =
        teamDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun addTeam(name: String): Long = teamDao.insert(Team(name = name).toEntity())

    override suspend fun renameTeam(team: Team) {
        teamDao.update(team.toEntity())
    }

    override suspend fun deleteTeam(team: Team) {
        teamDao.delete(team.toEntity())
    }

    override suspend fun ensureDefaultTeam(): Long {
        val existing = teamDao.observeAll().first()
        if (existing.isNotEmpty()) return existing.first().id
        return teamDao.insert(Team(name = DEFAULT_TEAM_NAME).toEntity())
    }
}
