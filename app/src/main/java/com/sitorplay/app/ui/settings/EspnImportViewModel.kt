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
import kotlinx.serialization.SerializationException
import java.io.IOException
import javax.inject.Inject

data class EspnImportUiState(
    val leagueId: String = "",
    val season: String = "",
    val isLoggedIn: Boolean = false,
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
        return EspnImportUiState(leagueId = saved.leagueId, season = saved.season)
    }

    fun updateLeagueId(value: String) = _uiState.update { it.copy(leagueId = value, error = null) }
    fun updateSeason(value: String) = _uiState.update { it.copy(season = value, error = null) }

    fun onLoggedIn() {
        _uiState.update { it.copy(isLoggedIn = true, error = null) }
    }

    fun fetchTeams() {
        val credentials = currentCredentials() ?: run {
            _uiState.update { it.copy(error = "Fill in both fields (league ID and season must be numbers).") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, teams = emptyList()) }
            runCatching { espnImportRepository.fetchLeagueTeams(credentials) }
                .onSuccess { teams ->
                    appSettingsRepository.setEspnCredentials(credentials)
                    _uiState.update { it.copy(isLoading = false, teams = teams, isLoggedIn = true) }
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
        if (state.leagueId.isBlank() || state.season.isBlank()) {
            return null
        }
        val leagueId = state.leagueId.trim()
        val season = state.season.trim()
        if (leagueId.toLongOrNull() == null || season.toIntOrNull() == null) {
            return null
        }
        return EspnCredentials(leagueId = leagueId, season = season)
    }

    private fun describeError(e: Throwable): String {
        val message = e.message.orEmpty()
        return when {
            message.contains("401") || message.contains("403") ->
                "ESPN rejected that session. Try logging in again."
            message.contains("404") -> "League not found for that league ID/season."
            e is IOException && message.contains("non-JSON response") -> message
            e is SerializationException || message.contains("json", ignoreCase = true) ||
                message.contains("html", ignoreCase = true) ->
                "ESPN didn't return the league data we expected. This usually means the login " +
                    "session is stale, the season doesn't match the league ID, or you're not a " +
                    "member of this league. Try logging in again."
            else -> message.ifBlank { "Couldn't reach ESPN. Check your connection and try again." }
        }
    }
}
