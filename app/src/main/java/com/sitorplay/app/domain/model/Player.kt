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
    val rosterSlot: RosterSlot,
    /** Sleeper's player_id, when this player was added via live search. Null for manual entries. */
    val externalId: String? = null
)

/** A real NFL player from the live Sleeper directory, before it's added to a roster. */
data class NflPlayer(
    val externalId: String,
    val name: String,
    val position: Position,
    val nflTeam: String,
    val injuryStatus: InjuryStatus
)

/** This week's live projection + matchup for an [NflPlayer], fetched on demand. */
data class WeeklyContext(
    val projectedPoints: Double,
    val opponent: String?,
    val injuryStatus: InjuryStatus
)
