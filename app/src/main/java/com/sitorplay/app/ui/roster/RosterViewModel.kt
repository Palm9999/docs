package com.sitorplay.app.ui.roster

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitorplay.app.data.repository.PlayerRepository
import com.sitorplay.app.domain.model.Recommendation
import com.sitorplay.app.domain.usecase.GetSitStartRecommendationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun removePlayer(recommendation: Recommendation) {
        viewModelScope.launch {
            playerRepository.removePlayer(recommendation.player)
        }
    }
}
