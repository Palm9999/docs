package com.sitorplay.app.domain.usecase

import com.sitorplay.app.domain.model.Call
import com.sitorplay.app.domain.model.InjuryStatus
import com.sitorplay.app.domain.model.Player
import com.sitorplay.app.domain.model.Position
import com.sitorplay.app.domain.model.Recommendation
import javax.inject.Inject

/**
 * Lineup slot counts for a standard single-QB fantasy roster. FLEX is filled from
 * whichever RB/WR/TE has the best adjusted projection left after the fixed slots.
 */
private val STARTER_SLOTS: Map<Position, Int> = mapOf(
    Position.QB to 1,
    Position.RB to 2,
    Position.WR to 2,
    Position.TE to 1,
    Position.K to 1,
    Position.DEF to 1
)
private const val FLEX_SLOTS = 1
private val FLEX_ELIGIBLE = setOf(Position.RB, Position.WR, Position.TE)

class GetSitStartRecommendationsUseCase @Inject constructor() {

    operator fun invoke(roster: List<Player>): List<Recommendation> {
        val recommendations = mutableMapOf<Long, Recommendation>()

        // Ruled-out players are never a legitimate start, no matter how thin the roster is.
        roster.filter { it.injuryStatus == InjuryStatus.OUT }.forEach { player ->
            recommendations[player.id] = Recommendation(
                player = player,
                call = Call.SIT,
                adjustedProjection = 0.0,
                reasons = listOf("Ruled OUT for this week's game — do not start")
            )
        }

        val scored = roster.filter { it.injuryStatus != InjuryStatus.OUT }
            .associateWith { adjustedProjection(it) }
        val started = mutableSetOf<Long>()

        STARTER_SLOTS.forEach { (position, slots) ->
            scored.filterKeys { it.position == position }
                .toList()
                .sortedByDescending { it.second }
                .forEachIndexed { index, (player, score) ->
                    val call = if (index < slots) Call.START else Call.SIT
                    if (call == Call.START) started += player.id
                    recommendations[player.id] = Recommendation(
                        player = player,
                        call = call,
                        adjustedProjection = score,
                        reasons = reasonsFor(player, score, index, slots)
                    )
                }
        }

        scored.filterKeys { it.position in FLEX_ELIGIBLE && it.id !in started }
            .toList()
            .sortedByDescending { it.second }
            .forEachIndexed { index, (player, score) ->
                val call = if (index < FLEX_SLOTS) Call.START else Call.SIT
                val flexReasons = reasonsFor(player, score, index, FLEX_SLOTS) +
                    listOfNotNull(if (call == Call.START) "Best remaining FLEX option" else null)
                recommendations[player.id] = Recommendation(
                    player = player,
                    call = call,
                    adjustedProjection = score,
                    reasons = flexReasons
                )
            }

        return roster.map { recommendations.getValue(it.id) }
    }

    private fun adjustedProjection(player: Player): Double = MatchupScoring.adjustedProjection(
        projectedPoints = player.projectedPoints,
        opponentDefenseRank = player.opponentDefenseRank,
        injuryStatus = player.injuryStatus
    )

    private fun reasonsFor(player: Player, score: Double, rankIndex: Int, slots: Int): List<String> {
        val reasons = mutableListOf<String>()
        reasons += "Projected ${"%.1f".format(player.projectedPoints)} pts vs ${player.opponent}"
        reasons += when {
            player.opponentDefenseRank <= 8 -> "Tough matchup (defense ranked #${player.opponentDefenseRank} vs ${player.position})"
            player.opponentDefenseRank <= 24 -> "Average matchup (defense ranked #${player.opponentDefenseRank} vs ${player.position})"
            else -> "Favorable matchup (defense ranked #${player.opponentDefenseRank} vs ${player.position})"
        }
        if (player.injuryStatus.multiplier < 1.0) {
            reasons += "Injury concern: ${player.injuryStatus.label}"
        }
        reasons += if (rankIndex < slots) {
            "Ranked #${rankIndex + 1} at ${player.position} on your roster this week"
        } else {
            "Outranked by ${slots} healthier/better-matched ${player.position}${if (slots == 1) "" else "s"} on your roster"
        }
        return reasons
    }
}
