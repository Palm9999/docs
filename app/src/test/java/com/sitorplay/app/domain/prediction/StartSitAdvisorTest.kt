package com.sitorplay.app.domain.prediction

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The start/sit call is the app's actual product, so these cover the behaviour
 * that distinguishes it from ranking by projected points: that the same two
 * players swap places depending on what the week demands.
 */
class StartSitAdvisorTest {

    // Same expected points, opposite shapes: one steady, one boom-or-bust.
    private val steady = Projection(PointsRange(floor = 9.0, median = 12.0, ceiling = 15.0), 1.0)
    private val volatile = Projection(PointsRange(floor = 2.0, median = 12.0, ceiling = 28.0), 1.0)

    @Test
    fun `the fitted distribution reproduces the quantiles it was given`() {
        val range = PointsRange(floor = 4.0, median = 11.0, ceiling = 21.0)
        val distribution = OutcomeDistribution.fromRange(range)

        assertEquals(0.15, distribution.cumulative(range.floor), 1e-6)
        assertEquals(0.50, distribution.cumulative(range.median), 1e-6)
        assertEquals(0.85, distribution.cumulative(range.ceiling), 1e-6)
    }

    @Test
    fun `erf stays within its stated error bound`() {
        // Reference values from the standard error function.
        listOf(
            0.0 to 0.0,
            0.5 to 0.5204998778,
            1.0 to 0.8427007929,
            2.0 to 0.9953222650,
            -1.5 to -0.9661051465
        ).forEach { (x, expected) ->
            assertEquals("erf($x)", expected, OutcomeDistribution.erf(x), 1.5e-7)
        }
    }

    @Test
    fun `holding a lead favours the steady player`() {
        // The rest of the lineup is already ahead; this slot needs almost nothing.
        val needed = StartSitAdvisor.pointsNeeded(opponentProjection = 100.0, myOtherStarters = 96.0)
        val comparison = StartSitAdvisor.compare(steady, volatile, needed)

        assertTrue("needed $needed should favour the floor", comparison.favoursA)
        assertTrue(comparison.probabilityA > comparison.probabilityB)
    }

    @Test
    fun `chasing a deficit favours the ceiling`() {
        val needed = StartSitAdvisor.pointsNeeded(opponentProjection = 120.0, myOtherStarters = 98.0)
        val comparison = StartSitAdvisor.compare(steady, volatile, needed)

        assertFalse("needing $needed should favour the ceiling", comparison.favoursA)
        assertTrue(comparison.probabilityB > comparison.probabilityA)
    }

    @Test
    fun `the call flips as the deficit grows, and the crossover is a real point`() {
        val probabilities = (0..30).map { needed ->
            StartSitAdvisor.compare(steady, volatile, needed.toDouble()).favoursA
        }
        assertTrue("steady should win when nothing is needed", probabilities.first())
        assertTrue("volatile should win when a lot is needed", !probabilities.last())
        // Exactly one crossover: the preference must not oscillate.
        val flips = probabilities.zipWithNext().count { (a, b) -> a != b }
        assertEquals("expected a single crossover, got $flips", 1, flips)
    }

    @Test
    fun `a higher projection can still be the wrong start`() {
        val better = Projection(PointsRange(8.0, 13.0, 18.0), 1.0)   // higher median
        val riskier = Projection(PointsRange(1.0, 11.0, 30.0), 1.0)  // lower median, huge ceiling

        val chasing = StartSitAdvisor.compare(better, riskier, pointsNeeded = 25.0)
        assertFalse("needing 25 should override the better projection", chasing.favoursA)
        assertTrue("this is the disagreement case the tool exists for",
            chasing.disagreesWithProjection)
        assertTrue(chasing.rationale("Better", "Riskier").contains("higher ceiling"))
    }

    @Test
    fun `a player who may not suit up is discounted, and zero still wins a won week`() {
        val questionable = Projection(steady.range, playProbability = 0.5)

        // Needing real points: half the time he is not there at all.
        val chasing = StartSitAdvisor.winProbability(questionable, pointsNeeded = 10.0)
        val healthy = StartSitAdvisor.winProbability(steady, pointsNeeded = 10.0)
        assertTrue(chasing < healthy)
        assertEquals(0.5 * healthy, chasing, 1e-9)

        // Already won regardless: scoring zero is still enough, so it does not
        // matter whether he plays. Tolerance is the erf approximation's own error
        // in the far tail, not slack in the logic.
        assertEquals(1.0, StartSitAdvisor.winProbability(questionable, -3.0), 1e-6)
    }

    @Test
    fun `a dead heat is reported as one rather than dressed up`() {
        val comparison = StartSitAdvisor.compare(steady, steady, pointsNeeded = 12.0)
        assertTrue(comparison.isTooCloseToCall)
        assertTrue(comparison.rationale("A", "B").startsWith("Too close to call"))
        assertTrue(abs(comparison.edge) < 1e-9)
    }

    @Test
    fun `a zero-width range degenerates to a step rather than dividing by zero`() {
        val certain = Projection(PointsRange(10.0, 10.0, 10.0), 1.0)
        assertEquals(1.0, StartSitAdvisor.winProbability(certain, 9.0), 1e-9)
        assertEquals(0.0, StartSitAdvisor.winProbability(certain, 11.0), 1e-9)
    }
}
