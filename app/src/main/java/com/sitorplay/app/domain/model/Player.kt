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
    /** Which [Team] this player is rostered on. Defaults to the first auto-created team. */
    val teamId: Long = 1L,
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
    val injuryStatus: InjuryStatus,
    val injuryBodyPart: String? = null,
    val injuryNotes: String? = null
)

enum class TrendDirection { ADD, DROP }

/** How much roster churn a player has seen league-wide in the last 24h, per Sleeper. */
data class TrendInfo(
    val direction: TrendDirection,
    val count: Int
)

/** This week's live projection + matchup for an [NflPlayer], fetched on demand. */
data class WeeklyContext(
    val projectedPoints: Double,
    val opponent: String?,
    val injuryStatus: InjuryStatus
)

/** Extra context for the player detail screen: injury notes and league-wide trend. */
data class PlayerDetailExtras(
    val trend: TrendInfo? = null,
    val injuryBodyPart: String? = null,
    val injuryNotes: String? = null
)

/** A trending-add player not on the current roster, suggested as a waiver-wire pickup. */
data class WaiverSuggestion(
    val player: NflPlayer,
    val trendCount: Int,
    val projectedPoints: Double,
    val opponent: String?
)
