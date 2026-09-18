package com.sitorplay.app.data.seed

import com.sitorplay.app.domain.model.InjuryStatus
import com.sitorplay.app.domain.model.Player
import com.sitorplay.app.domain.model.Position
import com.sitorplay.app.domain.model.RosterSlot

/**
 * Sample "week" of roster data used to seed a fresh install. Swap this out for a
 * real stats/projections API (e.g. by implementing a remote data source that maps
 * into [Player]) without touching the UI or recommendation logic.
 */
object SeedPlayers {
    fun sampleRoster(): List<Player> = listOf(
        Player(
            name = "Josh Allen", position = Position.QB, nflTeam = "BUF", opponent = "MIA",
            projectedPoints = 24.5, opponentDefenseRank = 22, injuryStatus = InjuryStatus.HEALTHY,
            rosterSlot = RosterSlot.STARTER
        ),
        Player(
            name = "Mac Jones", position = Position.QB, nflTeam = "JAX", opponent = "SF",
            projectedPoints = 14.2, opponentDefenseRank = 3, injuryStatus = InjuryStatus.HEALTHY,
            rosterSlot = RosterSlot.BENCH
        ),
        Player(
            name = "Christian McCaffrey", position = Position.RB, nflTeam = "SF", opponent = "JAX",
            projectedPoints = 21.0, opponentDefenseRank = 27, injuryStatus = InjuryStatus.QUESTIONABLE,
            rosterSlot = RosterSlot.STARTER
        ),
        Player(
            name = "Jonathan Taylor", position = Position.RB, nflTeam = "IND", opponent = "BAL",
            projectedPoints = 15.8, opponentDefenseRank = 6, injuryStatus = InjuryStatus.HEALTHY,
            rosterSlot = RosterSlot.STARTER
        ),
        Player(
            name = "Zack Moss", position = Position.RB, nflTeam = "CIN", opponent = "PIT",
            projectedPoints = 10.4, opponentDefenseRank = 18, injuryStatus = InjuryStatus.HEALTHY,
            rosterSlot = RosterSlot.BENCH
        ),
        Player(
            name = "CeeDee Lamb", position = Position.WR, nflTeam = "DAL", opponent = "NYG",
            projectedPoints = 19.6, opponentDefenseRank = 29, injuryStatus = InjuryStatus.HEALTHY,
            rosterSlot = RosterSlot.STARTER
        ),
        Player(
            name = "Tee Higgins", position = Position.WR, nflTeam = "CIN", opponent = "PIT",
            projectedPoints = 13.1, opponentDefenseRank = 9, injuryStatus = InjuryStatus.DOUBTFUL,
            rosterSlot = RosterSlot.STARTER
        ),
        Player(
            name = "Jayden Reed", position = Position.WR, nflTeam = "GB", opponent = "CHI",
            projectedPoints = 12.7, opponentDefenseRank = 25, injuryStatus = InjuryStatus.HEALTHY,
            rosterSlot = RosterSlot.BENCH
        ),
        Player(
            name = "Sam LaPorta", position = Position.TE, nflTeam = "DET", opponent = "MIN",
            projectedPoints = 11.9, opponentDefenseRank = 15, injuryStatus = InjuryStatus.HEALTHY,
            rosterSlot = RosterSlot.STARTER
        ),
        Player(
            name = "Cole Kmet", position = Position.TE, nflTeam = "CHI", opponent = "GB",
            projectedPoints = 8.3, opponentDefenseRank = 12, injuryStatus = InjuryStatus.HEALTHY,
            rosterSlot = RosterSlot.BENCH
        ),
        Player(
            name = "Justin Tucker", position = Position.K, nflTeam = "BAL", opponent = "IND",
            projectedPoints = 8.8, opponentDefenseRank = 20, injuryStatus = InjuryStatus.HEALTHY,
            rosterSlot = RosterSlot.STARTER
        ),
        Player(
            name = "49ers D/ST", position = Position.DEF, nflTeam = "SF", opponent = "JAX",
            projectedPoints = 9.5, opponentDefenseRank = 26, injuryStatus = InjuryStatus.HEALTHY,
            rosterSlot = RosterSlot.STARTER
        )
    )
}
