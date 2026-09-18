package com.sitorplay.app.ui.addplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitorplay.app.data.repository.PlayerRepository
import com.sitorplay.app.domain.model.InjuryStatus
import com.sitorplay.app.domain.model.Player
import com.sitorplay.app.domain.model.Position
import com.sitorplay.app.domain.model.RosterSlot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddPlayerFormState(
    val name: String = "",
    val position: Position = Position.RB,
    val nflTeam: String = "",
    val opponent: String = "",
    val projectedPoints: String = "",
    val opponentDefenseRank: String = "",
    val injuryStatus: InjuryStatus = InjuryStatus.HEALTHY
) {
    val isValid: Boolean
        get() = name.isNotBlank() && nflTeam.isNotBlank() && opponent.isNotBlank() &&
            projectedPoints.toDoubleOrNull() != null &&
            opponentDefenseRank.toIntOrNull()?.let { it in 1..32 } == true
}

@HiltViewModel
class AddPlayerViewModel @Inject constructor(
    private val playerRepository: PlayerRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(AddPlayerFormState())
    val formState: StateFlow<AddPlayerFormState> = _formState.asStateFlow()

    fun onNameChange(value: String) = update { it.copy(name = value) }
    fun onPositionChange(value: Position) = update { it.copy(position = value) }
    fun onTeamChange(value: String) = update { it.copy(nflTeam = value) }
    fun onOpponentChange(value: String) = update { it.copy(opponent = value) }
    fun onProjectedPointsChange(value: String) = update { it.copy(projectedPoints = value) }
    fun onDefenseRankChange(value: String) = update { it.copy(opponentDefenseRank = value) }
    fun onInjuryStatusChange(value: InjuryStatus) = update { it.copy(injuryStatus = value) }

    private inline fun update(block: (AddPlayerFormState) -> AddPlayerFormState) {
        _formState.value = block(_formState.value)
    }

    fun savePlayer(onSaved: () -> Unit) {
        val state = _formState.value
        if (!state.isValid) return
        viewModelScope.launch {
            playerRepository.addPlayer(
                Player(
                    name = state.name.trim(),
                    position = state.position,
                    nflTeam = state.nflTeam.trim().uppercase(),
                    opponent = state.opponent.trim().uppercase(),
                    projectedPoints = state.projectedPoints.toDouble(),
                    opponentDefenseRank = state.opponentDefenseRank.toInt(),
                    injuryStatus = state.injuryStatus,
                    rosterSlot = RosterSlot.BENCH
                )
            )
            onSaved()
        }
    }
}
