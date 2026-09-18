package com.sitorplay.app.ui.roster

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitorplay.app.data.repository.PlayerRepository
import com.sitorplay.app.data.sync.NflDataRepository
import com.sitorplay.app.domain.model.Recommendation
import com.sitorplay.app.domain.usecase.GetSitStartRecommendationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RosterUiState(
    val recommendations: List<Recommendation> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class RosterViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val nflDataRepository: NflDataRepository,
    private val getSitStartRecommendations: GetSitStartRecommendationsUseCase
) : ViewModel() {

    val uiState: StateFlow<RosterUiState> = playerRepository.observeRoster()
        .map { roster ->
            RosterUiState(
                recommendations = getSitStartRecommendations(roster),
                isLoading = false
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RosterUiState())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    fun removePlayer(recommendation: Recommendation) {
        viewModelScope.launch {
            playerRepository.removePlayer(recommendation.player)
        }
    }

    fun syncLiveData() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val currentRoster = playerRepository.observeRoster().first()
                val syncable = currentRoster.count { it.externalId != null }
                val updated = nflDataRepository.syncRoster(currentRoster)
                updated.filter { it.externalId != null }.forEach { playerRepository.updatePlayer(it) }
                _syncMessage.value = if (syncable == 0) {
                    "No live-linked players on your roster yet — add players via search to sync them."
                } else {
                    "Synced live projections for $syncable player${if (syncable == 1) "" else "s"}."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Covers network failures and any unexpected shape from these unofficial,
                // undocumented APIs — a sync hiccup should never crash the app.
                _syncMessage.value = "Couldn't reach live stats — check your connection and try again."
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun consumeSyncMessage() {
        _syncMessage.value = null
    }
}
