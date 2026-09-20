package com.sitorplay.app.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.sitorplay.app.domain.model.EspnCredentials
import com.sitorplay.app.domain.model.LineupSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY_SCORING_FORMAT = "scoring_format"
private const val KEY_SELECTED_TEAM_ID = "selected_team_id"
private const val NO_TEAM_SELECTED = -1L
private const val KEY_LINEUP_QB = "lineup_qb"
private const val KEY_LINEUP_RB = "lineup_rb"
private const val KEY_LINEUP_WR = "lineup_wr"
private const val KEY_LINEUP_TE = "lineup_te"
private const val KEY_LINEUP_FLEX = "lineup_flex"
private const val KEY_LINEUP_K = "lineup_k"
private const val KEY_LINEUP_DEF = "lineup_def"
private const val KEY_LOCK_REMINDERS_ENABLED = "lock_reminders_enabled"
private const val KEY_ESPN_LEAGUE_ID = "espn_league_id"
private const val KEY_ESPN_SEASON = "espn_season"
private const val KEY_ESPN_COOKIE_HEADER = "espn_cookie_header"

/** Small preference-backed settings shared across the app. */
@Singleton
class AppSettingsRepository @Inject constructor(
    private val preferences: SharedPreferences
) {
    private val _scoringFormat = MutableStateFlow(readScoringFormat())
    val scoringFormat: StateFlow<ScoringFormat> = _scoringFormat.asStateFlow()

    private val _selectedTeamId = MutableStateFlow(preferences.getLong(KEY_SELECTED_TEAM_ID, NO_TEAM_SELECTED))
    val selectedTeamId: StateFlow<Long> = _selectedTeamId.asStateFlow()

    private val _lineupSettings = MutableStateFlow(readLineupSettings())
    val lineupSettings: StateFlow<LineupSettings> = _lineupSettings.asStateFlow()

    private val _lockRemindersEnabled = MutableStateFlow(preferences.getBoolean(KEY_LOCK_REMINDERS_ENABLED, false))
    val lockRemindersEnabled: StateFlow<Boolean> = _lockRemindersEnabled.asStateFlow()

    private val _espnCredentials = MutableStateFlow(readEspnCredentials())
    val espnCredentials: StateFlow<EspnCredentials?> = _espnCredentials.asStateFlow()

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

    fun setLineupSettings(settings: LineupSettings) {
        preferences.edit {
            putInt(KEY_LINEUP_QB, settings.qb)
            putInt(KEY_LINEUP_RB, settings.rb)
            putInt(KEY_LINEUP_WR, settings.wr)
            putInt(KEY_LINEUP_TE, settings.te)
            putInt(KEY_LINEUP_FLEX, settings.flex)
            putInt(KEY_LINEUP_K, settings.k)
            putInt(KEY_LINEUP_DEF, settings.def)
        }
        _lineupSettings.value = settings
    }

    fun setLockRemindersEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_LOCK_REMINDERS_ENABLED, enabled) }
        _lockRemindersEnabled.value = enabled
    }

    /** Remembered so a re-import doesn't require retyping the ESPN cookie header. */
    fun setEspnCredentials(credentials: EspnCredentials) {
        preferences.edit {
            putString(KEY_ESPN_LEAGUE_ID, credentials.leagueId)
            putString(KEY_ESPN_SEASON, credentials.season)
            putString(KEY_ESPN_COOKIE_HEADER, credentials.cookieHeader)
        }
        _espnCredentials.value = credentials
    }

    private fun readEspnCredentials(): EspnCredentials? {
        val leagueId = preferences.getString(KEY_ESPN_LEAGUE_ID, null) ?: return null
        val season = preferences.getString(KEY_ESPN_SEASON, null) ?: return null
        val cookieHeader = preferences.getString(KEY_ESPN_COOKIE_HEADER, null) ?: return null
        return EspnCredentials(leagueId, season, cookieHeader)
    }

    private fun readScoringFormat(): ScoringFormat {
        val raw = preferences.getString(KEY_SCORING_FORMAT, null) ?: return ScoringFormat.PPR
        return runCatching { ScoringFormat.valueOf(raw) }.getOrDefault(ScoringFormat.PPR)
    }

    private fun readLineupSettings(): LineupSettings {
        val defaults = LineupSettings()
        return LineupSettings(
            qb = preferences.getInt(KEY_LINEUP_QB, defaults.qb),
            rb = preferences.getInt(KEY_LINEUP_RB, defaults.rb),
            wr = preferences.getInt(KEY_LINEUP_WR, defaults.wr),
            te = preferences.getInt(KEY_LINEUP_TE, defaults.te),
            flex = preferences.getInt(KEY_LINEUP_FLEX, defaults.flex),
            k = preferences.getInt(KEY_LINEUP_K, defaults.k),
            def = preferences.getInt(KEY_LINEUP_DEF, defaults.def)
        )
    }
}
