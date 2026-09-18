package com.sitorplay.app.domain.model

enum class Position {
    QB, RB, WR, TE, K, DEF
}

enum class InjuryStatus(val multiplier: Double, val label: String) {
    HEALTHY(1.0, "Healthy"),
    QUESTIONABLE(0.85, "Questionable"),
    DOUBTFUL(0.5, "Doubtful"),
    OUT(0.0, "Out")
}

enum class RosterSlot {
    STARTER, BENCH
}

/**
 * Opponent defense rank against this player's position: 1 = toughest matchup,
 * 32 = easiest matchup. Mirrors how fantasy sites publish "points allowed" ranks.
 */
data class Player(
    val id: Long = 0,
    val name: String,
    val position: Position,
    val nflTeam: String,
    val opponent: String,
    val projectedPoints: Double,
    val opponentDefenseRank: Int,
    val injuryStatus: InjuryStatus,
    val rosterSlot: RosterSlot
)
