package com.sitorplay.app.ui.playerdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitorplay.app.data.repository.PlayerRepository
import com.sitorplay.app.data.settings.AppSettingsRepository
import com.sitorplay.app.data.sync.NflDataRepository
import com.sitorplay.app.domain.model.PlayerDetailExtras
import com.sitorplay.app.data.prediction.PredictionRepository
import com.sitorplay.app.domain.model.Recommendation
import com.sitorplay.app.domain.prediction.FormStat
import com.sitorplay.app.domain.prediction.Projection
import com.sitorplay.app.domain.prediction.Scenario
import com.sitorplay.app.domain.prediction.WeeklyPlayer
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

/**
 * The "what if a team-mate sits?" panel.
 *
 * [baseline] is kept alongside [adjusted] so the UI can show the movement rather
 * than just the new number -- the change is the interesting part, and it is
 * often small enough that a bare figure would not read as a change at all.
 */
data class WhatIfUiState(
    val teammates: List<WeeklyPlayer> = emptyList(),
    val scenario: Scenario = Scenario(),
    val baseline: Projection? = null,
    val adjusted: Projection? = null
) {
    val isAvailable: Boolean get() = teammates.isNotEmpty() && baseline != null

    /** Points moved by the scenario; zero when nothing has been toggled. */
    val delta: Double
        get() = if (baseline == null || adjusted == null) 0.0
        else adjusted.range.median - baseline.range.median
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerDetailViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val nflDataRepository: NflDataRepository,
    private val getSitStartRecommendations: GetSitStartRecommendationsUseCase,
    private val predictionRepository: PredictionRepository,
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
                    appSettingsRepository.lineupSettings,
                    // Re-runs when a weekly bundle lands, so the detail screen
                    // picks up the model without needing a roster edit.
                    predictionRepository.status
                ) { roster, lineupSettings, _ ->
                    val projections = roster.mapNotNull { rostered ->
                        predictionRepository.projectionFor(rostered)?.let { rostered.id to it }
                    }.toMap()
                    getSitStartRecommendations(roster, lineupSettings, projections)
                        .find { it.player.id == playerId }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The stat line behind the projection.
     *
     * Recomputed when a bundle lands, same as the recommendation: without that
     * the table would sit empty until the user navigated away and back. Empty
     * when there is no bundle or the position is not modelled, which is the
     * signal the screen uses to leave the section out.
     */
    val form: StateFlow<List<FormStat>> = combine(
        recommendation,
        predictionRepository.status
    ) { current, _ ->
        current?.player?.let(predictionRepository::formFor).orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _extras = MutableStateFlow(PlayerDetailExtras())
    val extras: StateFlow<PlayerDetailExtras> = _extras.asStateFlow()

    private val _scenario = MutableStateFlow(Scenario())

    /**
     * Team-mates the user can move in or out, and what that does to this player.
     *
     * Empty when there is no weekly bundle, which is also the signal the UI uses
     * to hide the section rather than show a control that does nothing.
     */
    val whatIf: StateFlow<WhatIfUiState> = combine(
        recommendation,
        _scenario
    ) { current, scenario ->
        val player = current?.player ?: return@combine WhatIfUiState()
        val teammates = predictionRepository.teammatesOf(player.externalId)
        if (teammates.isEmpty()) return@combine WhatIfUiState()
        WhatIfUiState(
            teammates = teammates,
            scenario = scenario,
            baseline = predictionRepository.projectionFor(player),
            adjusted = predictionRepository.projectionFor(player, scenario)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhatIfUiState())

    fun toggleTeammate(teammate: WeeklyPlayer) {
        _scenario.value = _scenario.value.toggle(teammate.sleeperId, teammate.ruledOut)
    }

    fun clearScenario() {
        _scenario.value = Scenario()
    }

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
