package com.sitorplay.app.domain.usecase

import com.sitorplay.app.domain.model.Call
import com.sitorplay.app.domain.model.InjuryStatus
import com.sitorplay.app.domain.model.Player
import com.sitorplay.app.domain.model.Position
import com.sitorplay.app.domain.model.RosterSlot
import org.junit.Assert.assertEquals
import org.junit.Test

class GetSitStartRecommendationsUseCaseTest {

    private val useCase = GetSitStartRecommendationsUseCase()

    private fun rb(
        id: Long,
        name: String,
        projectedPoints: Double,
        defenseRank: Int = 16,
        injuryStatus: InjuryStatus = InjuryStatus.HEALTHY
    ) = Player(
        id = id,
        name = name,
        position = Position.RB,
        nflTeam = "AAA",
        opponent = "BBB",
        projectedPoints = projectedPoints,
        opponentDefenseRank = defenseRank,
        injuryStatus = injuryStatus,
        rosterSlot = RosterSlot.BENCH
    )

    // A trio of WRs (two clear starters plus a leftover) that out-scores whatever
    // leftover RB is under test, so that RB is left truly benched instead of
    // being swept into the FLEX slot for lack of any other option.
    private fun flexBlockingWrs(startId: Long) = listOf(
        wr(startId, projectedPoints = 20.0),
        wr(startId + 1, projectedPoints = 18.0),
        wr(startId + 2, projectedPoints = 12.0)
    )

    @Test
    fun `top two RBs by adjusted projection are started, rest sit`() {
        val roster = listOf(
            rb(1, "RB1", projectedPoints = 20.0),
            rb(2, "RB2", projectedPoints = 15.0),
            rb(3, "RB3", projectedPoints = 10.0)
        ) + flexBlockingWrs(startId = 90)

        val result = useCase(roster)

        assertEquals(Call.START, result.first { it.player.id == 1L }.call)
        assertEquals(Call.START, result.first { it.player.id == 2L }.call)
        assertEquals(Call.SIT, result.first { it.player.id == 3L }.call)
    }

    @Test
    fun `tough matchup can flip an otherwise higher projection to sit`() {
        val roster = listOf(
            rb(1, "Favorable matchup", projectedPoints = 12.0, defenseRank = 30),
            rb(2, "Locked-in starter", projectedPoints = 14.0, defenseRank = 16),
            rb(3, "Brutal matchup", projectedPoints = 13.0, defenseRank = 2)
        ) + flexBlockingWrs(startId = 90)

        val result = useCase(roster)

        assertEquals(Call.START, result.first { it.player.id == 1L }.call)
        assertEquals(Call.START, result.first { it.player.id == 2L }.call)
        assertEquals(Call.SIT, result.first { it.player.id == 3L }.call)
    }

    @Test
    fun `player ruled out is benched even with the best raw projection`() {
        val roster = listOf(
            rb(1, "Ruled out", projectedPoints = 22.0, injuryStatus = InjuryStatus.OUT),
            rb(2, "Healthy starter", projectedPoints = 10.0),
            rb(3, "Also healthy", projectedPoints = 9.0)
        )

        val result = useCase(roster)

        assertEquals(Call.SIT, result.first { it.player.id == 1L }.call)
        assertEquals(Call.START, result.first { it.player.id == 2L }.call)
        assertEquals(Call.START, result.first { it.player.id == 3L }.call)
    }

    private fun wr(id: Long, projectedPoints: Double) = Player(
        id = id, name = "WR$id", position = Position.WR, nflTeam = "AAA", opponent = "BBB",
        projectedPoints = projectedPoints, opponentDefenseRank = 16, injuryStatus = InjuryStatus.HEALTHY,
        rosterSlot = RosterSlot.BENCH
    )

    private fun te(id: Long, projectedPoints: Double) = Player(
        id = id, name = "TE$id", position = Position.TE, nflTeam = "AAA", opponent = "BBB",
        projectedPoints = projectedPoints, opponentDefenseRank = 16, injuryStatus = InjuryStatus.HEALTHY,
        rosterSlot = RosterSlot.BENCH
    )

    @Test
    fun `best remaining flex-eligible player is started in the flex slot`() {
        // RB and WR slots are full (2 each), leaving one leftover RB and one leftover WR
        // plus the lone TE (which fills its own slot, so has no leftover).
        val roster = listOf(
            rb(1, "RB starter 1", projectedPoints = 20.0),
            rb(2, "RB starter 2", projectedPoints = 18.0),
            rb(5, "RB leftover", projectedPoints = 8.0),
            wr(10, projectedPoints = 19.0),
            wr(11, projectedPoints = 17.0),
            wr(3, projectedPoints = 14.0), // leftover WR, beats the leftover RB for the flex spot
            te(20, projectedPoints = 6.0)
        )

        val result = useCase(roster)

        assertEquals(Call.START, result.first { it.player.id == 3L }.call)
        assertEquals(Call.SIT, result.first { it.player.id == 5L }.call)
    }
}
