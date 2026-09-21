package com.sitorplay.app.domain.usecase

import com.sitorplay.app.domain.model.InjuryStatus
import com.sitorplay.app.domain.model.PracticeParticipation
import com.sitorplay.app.domain.prediction.PlayRates

/**
 * The core "how good is this start this week" math, shared by the sit/start
 * recommendation engine and the player comparison tool so both always agree.
 */
object MatchupScoring {

    /**
     * Expected points: P(plays) * projection, adjusted for matchup.
     *
     * The injury term used to be a flat multiplier per designation -- 0.85 for
     * Questionable. Measured over 2023-2025, a Questionable player actually takes
     * the field about 61% of the time, and practice participation splits that from
     * 40% (did not practise) to 65% (full). Treating a coin-flip as an 85% start
     * overrated hurt players badly, so the multiplier is now a measured
     * probability; see `model/README.md`.
     */
    fun adjustedProjection(
        projectedPoints: Double,
        opponentDefenseRank: Int,
        injuryStatus: InjuryStatus,
        practiceParticipation: PracticeParticipation? = null,
        playRates: PlayRates = PlayRates.DEFAULT
    ): Double = projectedPoints *
        matchupMultiplier(opponentDefenseRank) *
        playRates.probability(injuryStatus, practiceParticipation)

    fun matchupMultiplier(defenseRank: Int): Double = when {
        defenseRank <= 8 -> 0.85
        defenseRank <= 24 -> 1.0
        else -> 1.15
    }
}
