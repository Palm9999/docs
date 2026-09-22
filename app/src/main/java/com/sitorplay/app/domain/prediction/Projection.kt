package com.sitorplay.app.domain.prediction

import com.sitorplay.app.domain.model.InjuryStatus
import com.sitorplay.app.domain.model.PracticeParticipation

/**
 * A player's predicted scoring range for one week, conditional on them playing.
 *
 * Three numbers rather than one because the lineup question is not "how many
 * points" but "which of these two do I start", and that depends on the shape of
 * the range: against a heavy favourite you want the better floor, as a heavy
 * underdog you need the better ceiling. [median] alone cannot express that.
 */
data class PointsRange(
    val floor: Double,
    val median: Double,
    val ceiling: Double
) {
    /** Width of the range; a rough read on how volatile a player is week to week. */
    val spread: Double get() = ceiling - floor
}

/**
 * A full projection: the conditional range plus the odds the player takes the field.
 *
 * These are kept apart deliberately. The model is trained only on players who
 * actually played, so its output answers "how many points if he suits up". The
 * chance he suits up is a separate, measured quantity -- see [playProbability].
 */
data class Projection(
    val range: PointsRange,
    val playProbability: Double
) {
    /**
     * What to actually rank a lineup on: P(play) * E[points | play].
     *
     * A Questionable player projected for 14 points who plays 61% of the time is
     * worth less than a healthy 10-point player, which the old flat 0.85
     * multiplier got wrong in both directions.
     */
    val expectedPoints: Double get() = range.median * playProbability

    /** Floor discounted the same way -- a player who may not play has no floor. */
    val expectedFloor: Double get() = range.floor * playProbability
}

/**
 * How often a player on the injury report actually takes the field.
 *
 * Measured over 2023-2025 rather than assumed; see `model/README.md`. The headline
 * is that "Questionable" is not one state: a Questionable player who practised in
 * full plays about 65% of the time, one who did not practise at all about 40%.
 * Treating those alike throws away the single most useful injury signal available
 * for free.
 */
class PlayRates(private val rates: Map<String, Double>) {

    fun probability(status: InjuryStatus, practice: PracticeParticipation?): Double {
        if (status == InjuryStatus.OUT) return 0.0
        if (status == InjuryStatus.HEALTHY) return 1.0

        val report = when (status) {
            InjuryStatus.DOUBTFUL -> "Doubtful"
            InjuryStatus.QUESTIONABLE -> "Questionable"
            else -> return 1.0
        }
        val practiceKey = when (practice) {
            PracticeParticipation.DID_NOT_PRACTICE -> "DNP"
            PracticeParticipation.LIMITED -> "Limited"
            PracticeParticipation.FULL -> "Full"
            null, PracticeParticipation.UNKNOWN -> null
        }

        practiceKey?.let { key -> rates["$report|$key"]?.let { return it } }
        // No practice report: fall back to the average across what we do know for
        // this designation, rather than silently assuming the best case.
        val forReport = rates.filterKeys { it.startsWith("$report|") }.values
        return if (forReport.isEmpty()) status.multiplier else forReport.average()
    }

    companion object {
        /**
         * Used when the model bundle is unavailable. Values are the measured rates
         * so behaviour degrades to the same numbers, not to the old assumption.
         */
        val DEFAULT = PlayRates(
            mapOf(
                "Questionable|Full" to 0.6545,
                "Questionable|Limited" to 0.6065,
                "Questionable|DNP" to 0.4041,
                "Doubtful|Limited" to 0.0147,
                "Doubtful|DNP" to 0.0
            )
        )
    }
}
