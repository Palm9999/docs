package com.sitorplay.app.ui.addplayer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitorplay.app.data.repository.PlayerRepository
import com.sitorplay.app.data.settings.AppSettingsRepository
import com.sitorplay.app.data.sync.NflDataRepository
import com.sitorplay.app.domain.model.InjuryStatus
import com.sitorplay.app.domain.model.NflPlayer
import com.sitorplay.app.domain.model.Player
import com.sitorplay.app.domain.model.Position
import com.sitorplay.app.domain.model.RosterSlot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Manual entry is kept as a fallback for a player the live feed doesn't have (yet). */
data class ManualPlayerFormState(
    val name: String = "",
    val position: Position = Position.RB,
    val nflTeam: String = "",
    val opponent: String = "",
    val projectedPoints: String = "",
    val opponentDefenseRank: String = "16",
    val injuryStatus: InjuryStatus = InjuryStatus.HEALTHY
) {
    val isValid: Boolean
        get() = name.isNotBlank() && nflTeam.isNotBlank() && opponent.isNotBlank() &&
            projectedPoints.toDoubleOrNull() != null &&
            opponentDefenseRank.toIntOrNull()?.let { it in 1..32 } == true
}

data class AddPlayerUiState(
    val manualMode: Boolean = false,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<NflPlayer> = emptyList(),
    val selectedPlayer: NflPlayer? = null,
    val isLoadingContext: Boolean = false,
    val projectedPoints: Double = 0.0,
    val opponent: String? = null,
    val opponentDefenseRank: String = "16",
    val manualForm: ManualPlayerFormState = ManualPlayerFormState()
) {
    val canSaveSelected: Boolean
        get() = selectedPlayer != null && opponentDefenseRank.toIntOrNull()?.let { it in 1..32 } == true
}

@OptIn(FlowPreview::class)
@HiltViewModel
class AddPlayerViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val nflDataRepository: NflDataRepository,
    private val appSettingsRepository: AppSettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddPlayerUiState())
    val uiState: StateFlow<AddPlayerUiState> = _uiState.asStateFlow()

    init {
        _uiState.asStateFlow()
            .map { it.searchQuery }
            .distinctUntilChanged()
            .debounce(300)
            .onEach { query -> runSearch(query) }
            .launchIn(viewModelScope)

        // Arrived here from a waiver-wire "quick add" tap: pre-select that player.
        savedStateHandle.get<String>("prefillExternalId")?.let { externalId ->
            viewModelScope.launch {
                nflDataRepository.getCachedPlayer(externalId)?.let { selectPlayer(it) }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query, isSearching = query.isNotBlank())
    }

    private suspend fun runSearch(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }
        // A failure here (no connection, or these unofficial APIs changing shape) should
        // surface as "no results" rather than breaking search for the rest of this screen.
        val results = runCatching { nflDataRepository.searchPlayers(query) }.getOrDefault(emptyList())
        // Ignore a stale response if the query has moved on since this search started.
        if (_uiState.value.searchQuery == query) {
            _uiState.value = _uiState.value.copy(searchResults = results, isSearching = false)
        }
    }

    fun selectPlayer(player: NflPlayer) {
        _uiState.value = _uiState.value.copy(selectedPlayer = player, isLoadingContext = true)
        viewModelScope.launch {
            val context = runCatching { nflDataRepository.getWeeklyContext(player.externalId) }.getOrNull()
            _uiState.value = _uiState.value.copy(
                isLoadingContext = false,
                projectedPoints = context?.projectedPoints ?: 0.0,
                opponent = context?.opponent
            )
        }
    }

    fun clearSelectedPlayer() {
        _uiState.value = _uiState.value.copy(selectedPlayer = null, projectedPoints = 0.0, opponent = null)
    }

    fun onDefenseRankChange(value: String) {
        _uiState.value = _uiState.value.copy(opponentDefenseRank = value)
    }

    fun toggleManualMode() {
        _uiState.value = _uiState.value.copy(manualMode = !_uiState.value.manualMode)
    }

    fun onManualFormChange(block: (ManualPlayerFormState) -> ManualPlayerFormState) {
        _uiState.value = _uiState.value.copy(manualForm = block(_uiState.value.manualForm))
    }

    fun saveSelectedPlayer(onSaved: () -> Unit) {
        val state = _uiState.value
        val selected = state.selectedPlayer ?: return
        val rank = state.opponentDefenseRank.toIntOrNull() ?: return
        viewModelScope.launch {
            playerRepository.addPlayer(
                Player(
                    teamId = appSettingsRepository.selectedTeamId.value,
                    name = selected.name,
                    position = selected.position,
                    nflTeam = selected.nflTeam,
                    opponent = state.opponent ?: "TBD",
                    projectedPoints = state.projectedPoints,
                    opponentDefenseRank = rank,
                    injuryStatus = selected.injuryStatus,
                    rosterSlot = RosterSlot.BENCH,
                    externalId = selected.externalId
                )
            )
            onSaved()
        }
    }

    fun saveManualPlayer(onSaved: () -> Unit) {
        val form = _uiState.value.manualForm
        if (!form.isValid) return
        viewModelScope.launch {
            playerRepository.addPlayer(
                Player(
                    teamId = appSettingsRepository.selectedTeamId.value,
                    name = form.name.trim(),
                    position = form.position,
                    nflTeam = form.nflTeam.trim().uppercase(),
                    opponent = form.opponent.trim().uppercase(),
                    projectedPoints = form.projectedPoints.toDouble(),
                    opponentDefenseRank = form.opponentDefenseRank.toInt(),
                    injuryStatus = form.injuryStatus,
                    rosterSlot = RosterSlot.BENCH,
                    externalId = null
                )
            )
            onSaved()
        }
    }
}
