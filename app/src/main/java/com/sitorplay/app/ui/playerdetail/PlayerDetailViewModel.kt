package com.sitorplay.app.ui.playerdetail

import androidx.lifecycle.SavedStateHandle
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

@HiltViewModel
class PlayerDetailViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val getSitStartRecommendations: GetSitStartRecommendationsUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val playerId: Long = checkNotNull(savedStateHandle["playerId"])

    val recommendation: StateFlow<Recommendation?> = playerRepository.observeRoster()
        .map { roster ->
            getSitStartRecommendations(roster).find { it.player.id == playerId }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun removePlayer(onRemoved: () -> Unit) {
        val current = recommendation.value ?: return
        viewModelScope.launch {
            playerRepository.removePlayer(current.player)
            onRemoved()
        }
    }
}
