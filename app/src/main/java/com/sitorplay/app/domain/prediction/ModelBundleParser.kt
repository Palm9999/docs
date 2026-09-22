package com.sitorplay.app.domain.prediction

import com.sitorplay.app.domain.model.Position
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads the model bundle written by `model/export_model.py`.
 *
 * Parsed with the untyped JSON tree rather than `@Serializable` classes: the tree
 * arrays are large and homogeneous, and reading them straight into primitive
 * arrays avoids materialising ~35,000 boxed node objects only to throw them away.
 */
object ModelBundleParser {

    /** Bumped only when the on-disk shape changes incompatibly. */
    const val SUPPORTED_FORMAT_VERSION = 1

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): PredictionModel {
        val root = json.parseToJsonElement(text).jsonObject

        val version = root["format_version"]?.jsonPrimitive?.int
            ?: error("model bundle has no format_version")
        require(version == SUPPORTED_FORMAT_VERSION) {
            "model bundle is format v$version, this build reads v$SUPPORTED_FORMAT_VERSION"
        }

        val featureNames = root.getValue("features").jsonArray.map { it.jsonPrimitive.content }
        val calibration = root["calibration"]?.jsonObject.orEmpty()
        val playRates = root["play_rates"]?.jsonObject.orEmpty()
            .mapValues { (_, v) -> v.jsonPrimitive.double }

        val positions = root.getValue("trees").jsonObject.mapNotNull { (name, byQuantile) ->
            // A position the app does not know about is skipped rather than fatal,
            // so a newer bundle can add one without breaking an older build.
            val position = Position.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
            val ensembles = byQuantile.jsonObject.mapValues { (_, trees) ->
                TreeEnsemble(trees.jsonArray.map { parseTree(it.jsonObject) })
            }.mapKeys { (quantile, _) -> quantile.toInt() }

            val factors = calibration[name]?.jsonArray
            position to PositionModel(
                quantiles = ensembles,
                lowerWidening = factors?.getOrNull(0)?.jsonPrimitive?.double ?: 1.0,
                upperWidening = factors?.getOrNull(1)?.jsonPrimitive?.double ?: 1.0
            )
        }.toMap()

        return PredictionModel(
            featureNames = featureNames,
            scoring = root["scoring"]?.jsonPrimitive?.content ?: "ppr",
            trainedThrough = root["trained_through"]?.jsonPrimitive?.content.orEmpty(),
            positions = positions,
            playRates = if (playRates.isEmpty()) PlayRates.DEFAULT else PlayRates(playRates)
        )
    }

    private fun parseTree(tree: JsonObject): DecisionTree = DecisionTree(
        feature = tree.ints("f"),
        threshold = tree.doubles("t"),
        flags = tree.ints("g"),
        left = tree.ints("l"),
        right = tree.ints("r"),
        leafValue = tree.doubles("v")
    )

    private fun JsonObject.ints(key: String): IntArray {
        val array = getValue(key) as JsonArray
        return IntArray(array.size) { array[it].jsonPrimitive.int }
    }

    private fun JsonObject.doubles(key: String): DoubleArray {
        val array = getValue(key) as JsonArray
        return DoubleArray(array.size) { array[it].jsonPrimitive.double }
    }
}

/**
 * Builds a feature vector in the exact order the model was trained on.
 *
 * Any feature the caller has no value for is left as NaN rather than zero.
 * LightGBM has explicit handling for a missing split input, and [DecisionTree]
 * reproduces it; substituting zero would look like a real observation of "no
 * usage at all" and quietly bias every prediction downward.
 */
object Features {

    fun vector(model: PredictionModel, values: Map<String, Double?>): DoubleArray =
        DoubleArray(model.featureNames.size) { index ->
            values[model.featureNames[index]] ?: Double.NaN
        }

    /** Names the caller supplied that the model has no slot for -- useful in tests. */
    fun unusedKeys(model: PredictionModel, values: Map<String, Double?>): Set<String> =
        values.keys - model.featureNames.toSet()
}
