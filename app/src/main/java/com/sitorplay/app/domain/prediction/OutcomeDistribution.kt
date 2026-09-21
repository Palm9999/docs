package com.sitorplay.app.domain.prediction

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * A player's week turned into a distribution, fitted to the three quantiles the
 * model predicts.
 *
 * Fantasy outcomes are right-skewed -- the downside is bounded near zero while the
 * upside is a three-touchdown game -- so a single normal fitted to the median
 * would understate ceilings and overstate floors. Two half-normals glued at the
 * median cost nothing extra and reproduce p15, p50 and p85 exactly, which is all
 * the model actually claims to know.
 *
 * The density is discontinuous at the median; the cumulative function, which is
 * the only thing used here, is continuous and monotone.
 */
class OutcomeDistribution(
    val median: Double,
    private val sigmaLow: Double,
    private val sigmaHigh: Double
) {

    /** P(score <= [points]). */
    fun cumulative(points: Double): Double {
        val sigma = if (points < median) sigmaLow else sigmaHigh
        if (sigma < EPSILON) return if (points < median) 0.0 else 1.0
        return 0.5 * (1.0 + erf((points - median) / (sigma * SQRT_2)))
    }

    /** P(score > [points]). */
    fun probabilityOver(points: Double): Double = 1.0 - cumulative(points)

    companion object {
        /** z such that the standard normal CDF equals 0.15 (and 0.85 by symmetry). */
        private const val Z_15 = 1.0364333894937898
        private const val SQRT_2 = 1.4142135623730951
        private const val EPSILON = 1e-9

        /**
         * Fits the distribution whose 15th, 50th and 85th percentiles are exactly
         * the values in [range].
         */
        fun fromRange(range: PointsRange): OutcomeDistribution = OutcomeDistribution(
            median = range.median,
            sigmaLow = (range.median - range.floor).coerceAtLeast(0.0) / Z_15,
            sigmaHigh = (range.ceiling - range.median).coerceAtLeast(0.0) / Z_15
        )

        /**
         * Abramowitz and Stegun 7.1.26. Maximum absolute error 1.5e-7, which is
         * several orders of magnitude below anything that could change a lineup
         * call, and avoids a dependency for one function.
         */
        internal fun erf(x: Double): Double {
            val sign = if (x < 0) -1.0 else 1.0
            val z = abs(x)
            val t = 1.0 / (1.0 + 0.3275911 * z)
            val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
                - 0.284496736) * t + 0.254829592) * t * exp(-z * z)
            return sign * y
        }
    }
}
