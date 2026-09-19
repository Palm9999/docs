package com.sitorplay.app.data.repository

import com.sitorplay.app.domain.model.Team
import kotlinx.coroutines.flow.Flow

interface TeamRepository {
    fun observeTeams(): Flow<List<Team>>
    suspend fun addTeam(name: String): Long
    suspend fun renameTeam(team: Team)
    suspend fun deleteTeam(team: Team)

    /** Returns the id of the first team, creating a default "My Team" if none exist yet. */
    suspend fun ensureDefaultTeam(): Long
}
