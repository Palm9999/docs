package com.sitorplay.app.ui.waiver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitorplay.app.data.repository.PlayerRepository
import com.sitorplay.app.data.settings.AppSettingsRepository
import com.sitorplay.app.data.sync.NflDataRepository
import com.sitorplay.app.domain.model.Position
import com.sitorplay.app.domain.model.WaiverSuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WaiverWireUiState(
    val isLoading: Boolean = true,
    val suggestions: List<WaiverSuggestion> = emptyList(),
    val positionFilter: Position? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class WaiverWireViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val nflDataRepository: NflDataRepository,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WaiverWireUiState())
    val uiState: StateFlow<WaiverWireUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun setPositionFilter(position: Position?) {
        _uiState.update { it.copy(positionFilter = position) }
        load()
    }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val teamId = appSettingsRepository.selectedTeamId.value
                val excluded = playerRepository.rosteredExternalIds(teamId)
                val suggestions = nflDataRepository.getWaiverSuggestions(excluded, _uiState.value.positionFilter)
                _uiState.update { it.copy(isLoading = false, suggestions = suggestions) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Couldn't load waiver suggestions — check your connection.")
                }
            }
        }
    }
}
