package com.sitorplay.app.domain.prediction

/**
 * One flattened decision tree from the exported LightGBM model.
 *
 * Nodes live in parallel primitive arrays rather than an object graph, so walking
 * a tree allocates nothing and stays cache-friendly even with a thousand of them
 * on the main thread. Children are encoded in a single array: a non-negative value
 * is the index of another internal node, a negative value is the leaf at
 * `value.inv()`.
 *
 * See `model/nflpredict/export.py` for the writer. The two must change together.
 */
class DecisionTree(
    private val feature: IntArray,
    private val threshold: DoubleArray,
    private val flags: IntArray,
    private val left: IntArray,
    private val right: IntArray,
    private val leafValue: DoubleArray
) {

    fun score(features: DoubleArray): Double {
        var node = 0
        while (true) {
            var value = features[feature[node]]
            val packed = flags[node]
            val missing = packed and MISSING_MASK
            val defaultLeft = (packed and DEFAULT_LEFT_BIT) != 0

            // LightGBM only routes NaN down the default branch when the split was
            // actually trained with missing values present. Everywhere else a NaN
            // is coerced to zero and compared normally, so a feature the app fails
            // to supply lands where LightGBM would put it rather than somewhere
            // plausible-looking but wrong.
            if (value.isNaN() && missing != MISSING_NAN) {
                value = 0.0
            }
            val goLeft = if ((missing == MISSING_ZERO && value == 0.0) ||
                (missing == MISSING_NAN && value.isNaN())
            ) {
                defaultLeft
            } else {
                value <= threshold[node]
            }

            val next = if (goLeft) left[node] else right[node]
            if (next < 0) return leafValue[next.inv()]
            node = next
        }
    }

    companion object {
        private const val MISSING_MASK = 0b11
        private const val DEFAULT_LEFT_BIT = 0b100

        /** Values must match `MISSING` in export.py. */
        private const val MISSING_ZERO = 1
        private const val MISSING_NAN = 2
    }
}

/**
 * The trees for a single quantile. A gradient-boosted prediction is the plain sum
 * of every tree's leaf, with no bias term or link function -- verified against
 * LightGBM's own `predict()` before this was written.
 */
class TreeEnsemble(private val trees: List<DecisionTree>) {

    fun score(features: DoubleArray): Double {
        var total = 0.0
        for (tree in trees) {
            total += tree.score(features)
        }
        return total
    }

    val size: Int get() = trees.size
}
