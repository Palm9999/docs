package com.sitorplay.app.domain.usecase

import com.sitorplay.app.domain.model.BYE_WEEK_OPPONENT
import com.sitorplay.app.domain.model.Call
import com.sitorplay.app.domain.model.InjuryStatus
import com.sitorplay.app.domain.model.LineupSettings
import com.sitorplay.app.domain.model.Player
import com.sitorplay.app.domain.model.Position
import com.sitorplay.app.domain.model.PracticeParticipation
import com.sitorplay.app.domain.prediction.PlayRates
import com.sitorplay.app.domain.model.Recommendation
import com.sitorplay.app.domain.prediction.Projection
import javax.inject.Inject

private val FLEX_ELIGIBLE = setOf(Position.RB, Position.WR, Position.TE)

class GetSitStartRecommendationsUseCase @Inject constructor() {

    /**
     * @param projections the model's output per player id, where it has any. A
     * player with a projection is ranked on it directly rather than through the
     * matchup and injury multipliers, because the model already accounts for both
     * -- opponent strength is one of its features and the play probability is
     * baked into expected points. Applying the multipliers on top would discount
     * the same two things twice.
     */
    operator fun invoke(
        roster: List<Player>,
        lineupSettings: LineupSettings = LineupSettings(),
        projections: Map<Long, Projection> = emptyMap()
    ): List<Recommendation> {
        val recommendations = mutableMapOf<Long, Recommendation>()

        // Ruled-out and bye-week players are never a legitimate start, no matter how thin the roster is.
        roster.filter { it.injuryStatus == InjuryStatus.OUT || it.opponent == BYE_WEEK_OPPONENT }
            .forEach { player ->
                val reason = if (player.opponent == BYE_WEEK_OPPONENT) {
                    "${player.nflTeam} is on a bye this week — no game to play"
                } else {
                    "Ruled OUT for this week's game — do not start"
                }
                recommendations[player.id] = Recommendation(
                    player = player,
                    call = Call.SIT,
                    adjustedProjection = 0.0,
                    reasons = listOf(reason)
                )
            }

        val scored = roster.filter { it.injuryStatus != InjuryStatus.OUT && it.opponent != BYE_WEEK_OPPONENT }
            .associateWith { player -> adjustedProjection(player, projections[player.id]) }
        val started = mutableSetOf<Long>()

        lineupSettings.starterSlots().forEach { (position, slots) ->
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
                        reasons = reasonsFor(player, score, index, slots, projections[player.id]),
                        projection = projections[player.id]
                    )
                }
        }

        if (lineupSettings.flex > 0) {
            scored.filterKeys { it.position in FLEX_ELIGIBLE && it.id !in started }
                .toList()
                .sortedByDescending { it.second }
                .forEachIndexed { index, (player, score) ->
                    val call = if (index < lineupSettings.flex) Call.START else Call.SIT
                    val flexReasons =
                        reasonsFor(player, score, index, lineupSettings.flex, projections[player.id]) +
                            listOfNotNull(if (call == Call.START) "Best remaining FLEX option" else null)
                    recommendations[player.id] = Recommendation(
                        player = player,
                        call = call,
                        adjustedProjection = score,
                        reasons = flexReasons,
                        projection = projections[player.id]
                    )
                }
        }

        // Any position with 0 slots this league (e.g. no kicker) still needs a recommendation entry.
        scored.keys.filter { it.id !in recommendations }.forEach { player ->
            recommendations[player.id] = Recommendation(
                player = player,
                call = Call.SIT,
                adjustedProjection = scored.getValue(player),
                reasons = listOf("Your league doesn't use a ${player.position} slot"),
                projection = projections[player.id]
            )
        }

        return roster.map { recommendations.getValue(it.id) }
    }

    private fun adjustedProjection(player: Player, projection: Projection?): Double =
        projection?.expectedPoints ?: MatchupScoring.adjustedProjection(
            projectedPoints = player.projectedPoints,
            opponentDefenseRank = player.opponentDefenseRank,
            injuryStatus = player.injuryStatus,
            practiceParticipation = player.practiceParticipation
        )

    private fun reasonsFor(
        player: Player,
        score: Double,
        rankIndex: Int,
        slots: Int,
        projection: Projection?
    ): List<String> {
        val reasons = mutableListOf<String>()
        reasons += if (projection != null) {
            "Model projects ${"%.1f".format(projection.range.median)} pts vs ${player.opponent} " +
                "(${"%.1f".format(projection.range.floor)}–${"%.1f".format(projection.range.ceiling)} range)"
        } else {
            "Projected ${"%.1f".format(player.projectedPoints)} pts vs ${player.opponent}"
        }
        reasons += when {
            player.opponentDefenseRank <= 8 -> "Tough matchup (defense ranked #${player.opponentDefenseRank} vs ${player.position})"
            player.opponentDefenseRank <= 24 -> "Average matchup (defense ranked #${player.opponentDefenseRank} vs ${player.position})"
            else -> "Favorable matchup (defense ranked #${player.opponentDefenseRank} vs ${player.position})"
        }
        if (player.injuryStatus != InjuryStatus.HEALTHY) {
            val odds = PlayRates.DEFAULT.probability(player.injuryStatus, player.practiceParticipation)
            val practice = player.practiceParticipation
                .takeIf { it != PracticeParticipation.UNKNOWN }
                ?.let { ", ${it.label.lowercase()}" }
                .orEmpty()
            reasons += "${player.injuryStatus.label}$practice — " +
                "played ${"%.0f".format(odds * 100)}% of the time historically"
        }
        reasons += if (rankIndex < slots) {
            "Ranked #${rankIndex + 1} at ${player.position} on your roster this week"
        } else {
            "Outranked by ${slots} healthier/better-matched ${player.position}${if (slots == 1) "" else "s"} on your roster"
        }
        return reasons
    }
}
