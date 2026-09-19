package com.sitorplay.app.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY_SCORING_FORMAT = "scoring_format"
private const val KEY_SELECTED_TEAM_ID = "selected_team_id"
private const val NO_TEAM_SELECTED = -1L

/** Small preference-backed settings shared across the app: scoring format and the active team. */
@Singleton
class AppSettingsRepository @Inject constructor(
    private val preferences: SharedPreferences
) {
    private val _scoringFormat = MutableStateFlow(readScoringFormat())
    val scoringFormat: StateFlow<ScoringFormat> = _scoringFormat.asStateFlow()

    private val _selectedTeamId = MutableStateFlow(preferences.getLong(KEY_SELECTED_TEAM_ID, NO_TEAM_SELECTED))
    val selectedTeamId: StateFlow<Long> = _selectedTeamId.asStateFlow()

    fun setScoringFormat(format: ScoringFormat) {
        preferences.edit { putString(KEY_SCORING_FORMAT, format.name) }
        _scoringFormat.value = format
    }

    fun setSelectedTeam(teamId: Long) {
        preferences.edit { putLong(KEY_SELECTED_TEAM_ID, teamId) }
        _selectedTeamId.value = teamId
    }

    /** Called once at startup to fall back to [teamId] if nothing (or a deleted team) is selected. */
    fun ensureSelectedTeam(teamId: Long) {
        if (_selectedTeamId.value == NO_TEAM_SELECTED) {
            setSelectedTeam(teamId)
        }
    }

    private fun readScoringFormat(): ScoringFormat {
        val raw = preferences.getString(KEY_SCORING_FORMAT, null) ?: return ScoringFormat.PPR
        return runCatching { ScoringFormat.valueOf(raw) }.getOrDefault(ScoringFormat.PPR)
    }
}
