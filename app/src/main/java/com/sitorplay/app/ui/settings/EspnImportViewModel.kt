package com.sitorplay.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitorplay.app.data.repository.EspnImportRepository
import com.sitorplay.app.data.settings.AppSettingsRepository
import com.sitorplay.app.domain.model.EspnCredentials
import com.sitorplay.app.domain.model.EspnLeagueTeam
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EspnImportUiState(
    val leagueId: String = "",
    val season: String = "",
    val espnS2: String = "",
    val swid: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val teams: List<EspnLeagueTeam> = emptyList(),
    val importedTeamName: String? = null
)

@HiltViewModel
class EspnImportViewModel @Inject constructor(
    private val espnImportRepository: EspnImportRepository,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<EspnImportUiState> = _uiState.asStateFlow()

    private fun initialState(): EspnImportUiState {
        val saved = appSettingsRepository.espnCredentials.value ?: return EspnImportUiState()
        return EspnImportUiState(
            leagueId = saved.leagueId,
            season = saved.season,
            espnS2 = saved.espnS2,
            swid = saved.swid
        )
    }

    fun updateLeagueId(value: String) = _uiState.update { it.copy(leagueId = value, error = null) }
    fun updateSeason(value: String) = _uiState.update { it.copy(season = value, error = null) }
    fun updateEspnS2(value: String) = _uiState.update { it.copy(espnS2 = value, error = null) }
    fun updateSwid(value: String) = _uiState.update { it.copy(swid = value, error = null) }

    fun fetchTeams() {
        val credentials = currentCredentials() ?: run {
            _uiState.update { it.copy(error = "Fill in all four fields first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, teams = emptyList()) }
            runCatching { espnImportRepository.fetchLeagueTeams(credentials) }
                .onSuccess { teams ->
                    appSettingsRepository.setEspnCredentials(credentials)
                    _uiState.update { it.copy(isLoading = false, teams = teams) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = describeError(e)) }
                }
        }
    }

    fun importTeam(team: EspnLeagueTeam) {
        val credentials = currentCredentials() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                espnImportRepository.importTeamAsNewTeam(credentials, team.espnTeamId, team.name)
            }
                .onSuccess { newTeamId ->
                    appSettingsRepository.setSelectedTeam(newTeamId)
                    _uiState.update {
                        it.copy(isLoading = false, teams = emptyList(), importedTeamName = team.name)
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = describeError(e)) }
                }
        }
    }

    fun consumeImportedTeamName() {
        _uiState.update { it.copy(importedTeamName = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun currentCredentials(): EspnCredentials? {
        val state = _uiState.value
        if (state.leagueId.isBlank() || state.season.isBlank() || state.espnS2.isBlank() || state.swid.isBlank()) {
            return null
        }
        return EspnCredentials(
            leagueId = state.leagueId.trim(),
            season = state.season.trim(),
            espnS2 = state.espnS2.trim(),
            swid = state.swid.trim()
        )
    }

    private fun describeError(e: Throwable): String = when {
        e.message?.contains("401") == true || e.message?.contains("403") == true ->
            "ESPN rejected those credentials. Double-check the league ID, espn_s2, and SWID values."
        e.message?.contains("404") == true -> "League not found for that ID/season."
        else -> e.message ?: "Couldn't reach ESPN. Check your connection and try again."
    }
}
