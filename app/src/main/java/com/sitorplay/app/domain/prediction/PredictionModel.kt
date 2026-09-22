package com.sitorplay.app.domain.prediction

import com.sitorplay.app.domain.model.InjuryStatus
import com.sitorplay.app.domain.model.Position
import com.sitorplay.app.domain.model.PracticeParticipation

/**
 * The three quantile ensembles for one position, plus its calibration factors.
 *
 * The quantile models are fitted independently, which leaves the raw p15-p85 band
 * covering the true score only about 65% of the time against a nominal 70%. The
 * calibration factors widen each half by a multiplier fitted on held-out weeks.
 * Shipping [raw] to a user would mean showing a floor that is wrong more often
 * than advertised, so [project] is the one callers should use.
 */
class PositionModel(
    private val quantiles: Map<Int, TreeEnsemble>,
    private val lowerWidening: Double,
    private val upperWidening: Double
) {

    /** Uncalibrated quantiles, ordered. Exposed so the parity test can check it. */
    fun raw(features: DoubleArray): PointsRange {
        val median = ensemble(MEDIAN).score(features)
        // Independently fitted quantiles can cross on thin slices; the ordering is
        // a promise the UI relies on, so it is enforced rather than assumed.
        val floor = minOf(ensemble(FLOOR).score(features), median)
        val ceiling = maxOf(ensemble(CEILING).score(features), median)
        return PointsRange(floor, median, ceiling)
    }

    fun project(features: DoubleArray): PointsRange = calibrate(raw(features))

    /**
     * Widens a raw range outward from its median.
     *
     * The floor is deliberately not clamped at zero. Fantasy scoring genuinely
     * goes negative -- a lost fumble is -2, interceptions cost a quarterback -- so
     * a slightly negative 15th percentile is a real statement about a marginal
     * player, and clamping it broke the floor <= median ordering that callers rely
     * on whenever the median itself was near zero. Round for display at the edge
     * instead, where losing the information costs nothing.
     */
    fun calibrate(range: PointsRange): PointsRange = PointsRange(
        floor = range.median - lowerWidening * (range.median - range.floor),
        median = range.median,
        ceiling = range.median + upperWidening * (range.ceiling - range.median)
    )

    private fun ensemble(quantile: Int): TreeEnsemble =
        quantiles[quantile] ?: error("model is missing the p$quantile ensemble")

    companion object {
        const val FLOOR = 15
        const val MEDIAN = 50
        const val CEILING = 85
    }
}

/**
 * Everything the app needs to turn a feature vector into a lineup decision.
 *
 * Parsed from `model.json.gz`, which is produced by `model/export_model.py`. The
 * feature order in [featureNames] is part of the contract: [Features.vector]
 * builds rows against it, and a mismatch would silently score nonsense, so the
 * bundle carries the names rather than relying on both sides agreeing.
 */
class PredictionModel(
    val featureNames: List<String>,
    val scoring: String,
    val trainedThrough: String,
    private val positions: Map<Position, PositionModel>,
    val playRates: PlayRates
) {

    fun supports(position: Position): Boolean = position in positions

    fun rangeFor(position: Position, features: DoubleArray): PointsRange? =
        positions[position]?.project(features)

    fun rawRangeFor(position: Position, features: DoubleArray): PointsRange? =
        positions[position]?.raw(features)

    /**
     * The full projection for a player: scoring range times the odds they play.
     *
     * Returns null for a position with no model -- kickers and defences are not
     * modelled -- so callers can fall back to the plain projection rather than
     * being handed a fabricated number.
     */
    fun project(
        position: Position,
        features: DoubleArray,
        injuryStatus: InjuryStatus,
        practice: PracticeParticipation? = null
    ): Projection? {
        val range = rangeFor(position, features) ?: return null
        return Projection(range, playRates.probability(injuryStatus, practice))
    }
}
