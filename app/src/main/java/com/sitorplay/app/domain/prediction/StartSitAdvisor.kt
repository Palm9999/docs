package com.sitorplay.app.domain.prediction

/**
 * Picks between two players for one lineup slot.
 *
 * Ranking by projected points answers the wrong question. What decides a fantasy
 * week is whether you outscore one specific opponent, so the right comparison is
 * P(this player gets me over the line) -- and that flips with the situation. Hold
 * a comfortable lead and the steady player wins because you only need a floor;
 * face a big deficit and the boom-or-bust player wins because the safe option
 * cannot get you there however reliable it is.
 *
 * Expected points cannot express that. Two players with identical medians and
 * different spreads are genuinely different starts, and this is where the
 * model's quantiles earn their place over a single number.
 */
object StartSitAdvisor {

    /** How many points this slot must produce to win, given the rest of the matchup. */
    fun pointsNeeded(opponentProjection: Double, myOtherStarters: Double): Double =
        opponentProjection - myOtherStarters

    /**
     * P(this player alone clears [pointsNeeded]).
     *
     * A player who does not suit up scores zero, which still wins the week if the
     * slot did not need anything -- so the two ways of failing are kept separate
     * rather than folded into a single discounted projection.
     */
    fun winProbability(projection: Projection, pointsNeeded: Double): Double {
        val ifPlays = OutcomeDistribution.fromRange(projection.range).probabilityOver(pointsNeeded)
        val ifOut = if (pointsNeeded < 0.0) 1.0 else 0.0
        return projection.playProbability * ifPlays + (1.0 - projection.playProbability) * ifOut
    }

    /**
     * Which of two players to start, given how much this slot has to produce.
     *
     * Derive [pointsNeeded] with [pointsNeeded]: a favoured lineup needs less from
     * the slot, which is exactly what tilts the call toward the safer player.
     */
    fun compare(
        a: Projection,
        b: Projection,
        pointsNeeded: Double
    ): StartSitComparison {
        val probabilityA = winProbability(a, pointsNeeded)
        val probabilityB = winProbability(b, pointsNeeded)
        return StartSitComparison(
            pointsNeeded = pointsNeeded,
            probabilityA = probabilityA,
            probabilityB = probabilityB,
            expectedA = a.expectedPoints,
            expectedB = b.expectedPoints
        )
    }
}

/**
 * The outcome of a head-to-head, carrying both answers so the UI can show when
 * they disagree -- which is the interesting case and the whole point of the tool.
 */
data class StartSitComparison(
    val pointsNeeded: Double,
    val probabilityA: Double,
    val probabilityB: Double,
    val expectedA: Double,
    val expectedB: Double
) {
    /** True when the higher-scoring player on average is not the better start here. */
    val disagreesWithProjection: Boolean
        get() = favoursA != (expectedA > expectedB) && !isTooCloseToCall

    val favoursA: Boolean get() = probabilityA >= probabilityB

    val edge: Double get() = kotlin.math.abs(probabilityA - probabilityB)

    /** Below this the model is not saying anything a user should act on. */
    val isTooCloseToCall: Boolean get() = edge < 0.02

    /**
     * Plain-English reason, tied to what actually drove the call rather than a
     * restatement of the numbers.
     */
    fun rationale(nameA: String, nameB: String): String {
        if (isTooCloseToCall) {
            return "Too close to call — either start is within 2% of the same win chance."
        }
        val winner = if (favoursA) nameA else nameB
        val loser = if (favoursA) nameB else nameA
        val winnerExpected = if (favoursA) expectedA else expectedB
        val loserExpected = if (favoursA) expectedB else expectedA

        return when {
            !disagreesWithProjection ->
                "$winner — better projection and the better odds of covering " +
                    "${format(pointsNeeded)} points."
            pointsNeeded > winnerExpected ->
                "$winner — you need ${format(pointsNeeded)} points from this slot, " +
                    "more than either is projected for, so the higher ceiling is worth " +
                    "more than $loser's steadier ${format(loserExpected)}."
            else ->
                "$winner — you only need ${format(pointsNeeded)} points here, so the " +
                    "safer floor beats $loser's higher projection of ${format(loserExpected)}."
        }
    }

    private fun format(value: Double): String = "%.1f".format(value)
}
