package com.sitorplay.app.ui.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitorplay.app.data.sync.NflDataRepository
import com.sitorplay.app.domain.model.NflPlayer
import com.sitorplay.app.domain.usecase.MatchupScoring
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CompareSlot { A, B }

data class ComparisonPlayer(
    val player: NflPlayer,
    val projectedPoints: Double = 0.0,
    val opponent: String? = null,
    val opponentDefenseRank: String = "16",
    val isLoadingContext: Boolean = false
) {
    val adjustedProjection: Double
        get() = opponentDefenseRank.toIntOrNull()?.let {
            MatchupScoring.adjustedProjection(projectedPoints, it, player.injuryStatus)
        } ?: 0.0
}

data class CompareUiState(
    val activeSlot: CompareSlot = CompareSlot.A,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<NflPlayer> = emptyList(),
    val slotA: ComparisonPlayer? = null,
    val slotB: ComparisonPlayer? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class CompareViewModel @Inject constructor(
    private val nflDataRepository: NflDataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

    init {
        _uiState.asStateFlow()
            .map { it.searchQuery }
            .distinctUntilChanged()
            .debounce(300)
            .onEach { query -> runSearch(query) }
            .launchIn(viewModelScope)
    }

    fun setActiveSlot(slot: CompareSlot) {
        _uiState.update { it.copy(activeSlot = slot, searchQuery = "", searchResults = emptyList()) }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearching = query.isNotBlank()) }
    }

    private suspend fun runSearch(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        val results = runCatching { nflDataRepository.searchPlayers(query) }.getOrDefault(emptyList())
        if (_uiState.value.searchQuery == query) {
            _uiState.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    fun selectPlayer(player: NflPlayer) {
        val slot = _uiState.value.activeSlot
        val comparisonPlayer = ComparisonPlayer(player = player, isLoadingContext = true)
        _uiState.update {
            if (slot == CompareSlot.A) it.copy(slotA = comparisonPlayer) else it.copy(slotB = comparisonPlayer)
        }
        viewModelScope.launch {
            val context = runCatching { nflDataRepository.getWeeklyContext(player.externalId) }.getOrNull()
            _uiState.update { state ->
                val update: (ComparisonPlayer?) -> ComparisonPlayer? = { current ->
                    current?.copy(
                        isLoadingContext = false,
                        projectedPoints = context?.projectedPoints ?: 0.0,
                        opponent = context?.opponent
                    )
                }
                if (slot == CompareSlot.A) state.copy(slotA = update(state.slotA))
                else state.copy(slotB = update(state.slotB))
            }
        }
    }

    fun onDefenseRankChange(slot: CompareSlot, value: String) {
        _uiState.update { state ->
            if (slot == CompareSlot.A) state.copy(slotA = state.slotA?.copy(opponentDefenseRank = value))
            else state.copy(slotB = state.slotB?.copy(opponentDefenseRank = value))
        }
    }

    fun clearSlot(slot: CompareSlot) {
        _uiState.update {
            if (slot == CompareSlot.A) it.copy(slotA = null) else it.copy(slotB = null)
        }
    }
}
