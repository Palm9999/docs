package com.sitorplay.app.domain.usecase

import com.sitorplay.app.domain.model.Call
import com.sitorplay.app.domain.model.InjuryStatus
import com.sitorplay.app.domain.model.LineupSettings
import com.sitorplay.app.domain.model.Player
import com.sitorplay.app.domain.model.Position
import com.sitorplay.app.domain.model.PracticeParticipation
import com.sitorplay.app.domain.model.RosterSlot
import com.sitorplay.app.domain.prediction.PointsRange
import com.sitorplay.app.domain.prediction.Projection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the model taking over from the heuristic: that it drives the ranking
 * when present, that the two coexist on one roster, and that nothing is
 * discounted twice along the way.
 */
class ModelBackedRecommendationsTest {

    private val useCase = GetSitStartRecommendationsUseCase()
    private val oneWideReceiver = LineupSettings(qb = 0, rb = 0, wr = 1, te = 0, flex = 0, k = 0, def = 0)

    private fun receiver(
        id: Long,
        name: String,
        projected: Double,
        defenseRank: Int = 16,
        injury: InjuryStatus = InjuryStatus.HEALTHY,
        practice: PracticeParticipation = PracticeParticipation.UNKNOWN
    ) = Player(
        id = id,
        name = name,
        position = Position.WR,
        nflTeam = "SEA",
        opponent = "SF",
        projectedPoints = projected,
        opponentDefenseRank = defenseRank,
        injuryStatus = injury,
        practiceParticipation = practice,
        rosterSlot = RosterSlot.BENCH,
        externalId = "sleeper-$id"
    )

    @Test
    fun `the model overrides the projection it disagrees with`() {
        val roster = listOf(
            receiver(1, "Higher projection", projected = 15.0),
            receiver(2, "Model favourite", projected = 9.0)
        )
        // The model rates the player the projection has lower.
        val projections = mapOf(
            1L to Projection(PointsRange(3.0, 8.0, 14.0), 1.0),
            2L to Projection(PointsRange(9.0, 13.0, 18.0), 1.0)
        )

        val results = useCase(roster, oneWideReceiver, projections).associateBy { it.player.id }
        assertEquals(Call.SIT, results.getValue(1L).call)
        assertEquals(Call.START, results.getValue(2L).call)
        assertEquals(13.0, results.getValue(2L).adjustedProjection, 1e-9)
    }

    @Test
    fun `a model-ranked player is not discounted twice`() {
        // A brutal matchup and a Questionable tag would both knock the heuristic
        // down; the model already priced them in, so only its own play
        // probability should apply.
        val player = receiver(
            1, "Questionable", projected = 20.0, defenseRank = 1,
            injury = InjuryStatus.QUESTIONABLE, practice = PracticeParticipation.LIMITED
        )
        val projection = Projection(PointsRange(4.0, 12.0, 20.0), playProbability = 0.6065)

        val result = useCase(listOf(player), oneWideReceiver, mapOf(1L to projection)).single()

        assertEquals(12.0 * 0.6065, result.adjustedProjection, 1e-9)
        // The heuristic would have produced something quite different.
        val heuristic = MatchupScoring.adjustedProjection(
            20.0, 1, InjuryStatus.QUESTIONABLE, PracticeParticipation.LIMITED
        )
        assertTrue(kotlin.math.abs(heuristic - result.adjustedProjection) > 1.0)
    }

    @Test
    fun `players the model has never seen fall back to the heuristic`() {
        val roster = listOf(
            receiver(1, "In the bundle", projected = 10.0),
            receiver(2, "Not in the bundle", projected = 10.0)
        )
        val projections = mapOf(1L to Projection(PointsRange(2.0, 6.0, 11.0), 1.0))

        val results = useCase(roster, oneWideReceiver, projections).associateBy { it.player.id }
        assertNotNull(results.getValue(1L).projection)
        assertNull(results.getValue(2L).projection)

        // The unmodelled player keeps his heuristic score and so wins the slot.
        assertEquals(10.0, results.getValue(2L).adjustedProjection, 1e-9)
        assertEquals(Call.START, results.getValue(2L).call)
    }

    @Test
    fun `the reason names the range when the model supplied one`() {
        val projection = Projection(PointsRange(4.2, 11.5, 19.8), 1.0)
        val result = useCase(
            listOf(receiver(1, "Ranged", projected = 10.0)), oneWideReceiver, mapOf(1L to projection)
        ).single()

        val headline = result.reasons.first()
        assertTrue(headline, headline.contains("Model projects 11.5"))
        assertTrue(headline, headline.contains("4.2") && headline.contains("19.8"))
    }

    @Test
    fun `omitting projections leaves the existing behaviour untouched`() {
        val roster = listOf(receiver(1, "Only option", projected = 12.0, defenseRank = 30))
        val withoutModel = useCase(roster, oneWideReceiver).single()
        val expected = MatchupScoring.adjustedProjection(
            12.0, 30, InjuryStatus.HEALTHY, PracticeParticipation.UNKNOWN
        )
        assertEquals(expected, withoutModel.adjustedProjection, 1e-9)
        assertNull(withoutModel.projection)
    }

    @Test
    fun `a ruled-out player stays a sit even with a strong projection`() {
        val player = receiver(1, "Ruled out", projected = 22.0, injury = InjuryStatus.OUT)
        val projection = Projection(PointsRange(10.0, 18.0, 26.0), 1.0)

        val result = useCase(listOf(player), oneWideReceiver, mapOf(1L to projection)).single()
        assertEquals(Call.SIT, result.call)
        assertEquals(0.0, result.adjustedProjection, 1e-9)
    }
}
