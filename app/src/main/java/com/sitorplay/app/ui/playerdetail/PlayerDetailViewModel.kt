package com.sitorplay.app.ui.playerdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitorplay.app.data.repository.PlayerRepository
import com.sitorplay.app.data.settings.AppSettingsRepository
import com.sitorplay.app.data.sync.NflDataRepository
import com.sitorplay.app.domain.model.PlayerDetailExtras
import com.sitorplay.app.domain.model.Recommendation
import com.sitorplay.app.domain.usecase.GetSitStartRecommendationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerDetailViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val nflDataRepository: NflDataRepository,
    private val getSitStartRecommendations: GetSitStartRecommendationsUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val playerId: Long = checkNotNull(savedStateHandle["playerId"])

    val recommendation: StateFlow<Recommendation?> = playerRepository.observePlayer(playerId)
        .flatMapLatest { player ->
            if (player == null) {
                flowOf(null)
            } else {
                combine(
                    playerRepository.observeRoster(player.teamId),
                    appSettingsRepository.lineupSettings
                ) { roster, lineupSettings ->
                    getSitStartRecommendations(roster, lineupSettings).find { it.player.id == playerId }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _extras = MutableStateFlow(PlayerDetailExtras())
    val extras: StateFlow<PlayerDetailExtras> = _extras.asStateFlow()

    private val _isLoadingExtras = MutableStateFlow(false)
    val isLoadingExtras: StateFlow<Boolean> = _isLoadingExtras.asStateFlow()

    init {
        viewModelScope.launch {
            recommendation
                .mapNotNull { it?.player?.externalId }
                .distinctUntilChanged()
                .collectLatest { externalId ->
                    _isLoadingExtras.value = true
                    _extras.value = runCatching { nflDataRepository.getPlayerDetailExtras(externalId) }
                        .getOrDefault(PlayerDetailExtras())
                    _isLoadingExtras.value = false
                }
        }
    }

    fun removePlayer(onRemoved: () -> Unit) {
        val current = recommendation.value ?: return
        viewModelScope.launch {
            playerRepository.removePlayer(current.player)
            onRemoved()
        }
    }
}
