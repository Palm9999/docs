package com.sitorplay.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitorplay.app.data.repository.TeamRepository
import com.sitorplay.app.data.settings.AppSettingsRepository
import com.sitorplay.app.data.settings.ScoringFormat
import com.sitorplay.app.domain.model.LineupSettings
import com.sitorplay.app.domain.model.Team
import com.sitorplay.app.notification.LineupReminderScheduler
import com.sitorplay.app.notification.showLineupReminderNotification
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val selectedTeamId: Long = -1L,
    val lineupSettings: LineupSettings = LineupSettings(),
    val lockRemindersEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val teamRepository: TeamRepository,
    private val appSettingsRepository: AppSettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        teamRepository.observeTeams(),
        appSettingsRepository.selectedTeamId,
        appSettingsRepository.scoringFormat,
        appSettingsRepository.lineupSettings,
        appSettingsRepository.lockRemindersEnabled
    ) { teams, selectedTeamId, format, lineupSettings, remindersEnabled ->
        SettingsUiState(
            scoringFormat = format,
            teams = teams,
            selectedTeamId = selectedTeamId,
            lineupSettings = lineupSettings,
            lockRemindersEnabled = remindersEnabled
        )
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

    fun updateLineupSettings(update: (LineupSettings) -> LineupSettings) {
        appSettingsRepository.setLineupSettings(update(uiState.value.lineupSettings))
    }

    fun setLockRemindersEnabled(enabled: Boolean) {
        appSettingsRepository.setLockRemindersEnabled(enabled)
        if (enabled) {
            LineupReminderScheduler.schedule(context)
        } else {
            LineupReminderScheduler.cancel(context)
        }
    }

    fun sendTestNotification() {
        showLineupReminderNotification(context)
    }
}
