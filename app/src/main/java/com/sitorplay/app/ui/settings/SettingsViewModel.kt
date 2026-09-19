package com.sitorplay.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitorplay.app.data.repository.TeamRepository
import com.sitorplay.app.data.settings.AppSettingsRepository
import com.sitorplay.app.data.settings.ScoringFormat
import com.sitorplay.app.domain.model.Team
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val scoringFormat: ScoringFormat = ScoringFormat.PPR,
    val teams: List<Team> = emptyList(),
    val selectedTeamId: Long = -1L
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val teamRepository: TeamRepository,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        teamRepository.observeTeams(),
        appSettingsRepository.selectedTeamId,
        appSettingsRepository.scoringFormat
    ) { teams, selectedTeamId, format ->
        SettingsUiState(scoringFormat = format, teams = teams, selectedTeamId = selectedTeamId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun selectScoringFormat(format: ScoringFormat) {
        appSettingsRepository.setScoringFormat(format)
    }

    fun selectTeam(teamId: Long) {
        appSettingsRepository.setSelectedTeam(teamId)
    }

    fun addTeam(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = teamRepository.addTeam(name.trim())
            appSettingsRepository.setSelectedTeam(id)
        }
    }

    fun renameTeam(team: Team, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            teamRepository.renameTeam(team.copy(name = newName.trim()))
        }
    }

    fun deleteTeam(team: Team) {
        viewModelScope.launch {
            val remaining = teamRepository.observeTeams().first()
            if (remaining.size <= 1) return@launch // always keep at least one team
            teamRepository.deleteTeam(team)
            if (appSettingsRepository.selectedTeamId.value == team.id) {
                remaining.firstOrNull { it.id != team.id }?.let { appSettingsRepository.setSelectedTeam(it.id) }
            }
        }
    }
}
