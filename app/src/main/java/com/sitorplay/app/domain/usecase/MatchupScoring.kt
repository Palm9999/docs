package com.sitorplay.app.domain.usecase

import com.sitorplay.app.domain.model.InjuryStatus

/**
 * The core "how good is this start this week" math, shared by the sit/start
 * recommendation engine and the player comparison tool so both always agree.
 */
object MatchupScoring {

    fun adjustedProjection(
        projectedPoints: Double,
        opponentDefenseRank: Int,
        injuryStatus: InjuryStatus
    ): Double = projectedPoints * matchupMultiplier(opponentDefenseRank) * injuryStatus.multiplier

    fun matchupMultiplier(defenseRank: Int): Double = when {
        defenseRank <= 8 -> 0.85
        defenseRank <= 24 -> 1.0
        else -> 1.15
    }
}
