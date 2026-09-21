package com.sitorplay.app.domain.prediction

import com.sitorplay.app.domain.model.InjuryStatus
import com.sitorplay.app.domain.model.Position
import com.sitorplay.app.domain.model.PracticeParticipation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One player's pre-computed feature vector for the week being projected.
 *
 * The phone has no business downloading a season of play-by-play to work out a
 * rolling target share, so the features arrive ready-made from
 * `model/build_week.py` and only the scoring happens on device. That keeps
 * predictions instant and offline while still letting the app re-score locally
 * when something changes -- an injury designation flipping on Sunday morning, or
 * the user asking what happens if a team-mate sits.
 */
data class WeeklyPlayer(
    val sleeperId: String,
    val name: String,
    val position: Position,
    val team: String,
    val opponent: String?,
    val features: DoubleArray
) {
    // DoubleArray gives this class reference equality by default, which silently
    // breaks any set or map keyed on it.
    override fun equals(other: Any?): Boolean =
        this === other || (other is WeeklyPlayer && sleeperId == other.sleeperId)

    override fun hashCode(): Int = sleeperId.hashCode()
}

/**
 * A week's worth of feature vectors, keyed by the Sleeper id the roster stores.
 */
class WeeklyBundle(
    val season: Int,
    val week: Int,
    val generated: String,
    private val bySleeperId: Map<String, WeeklyPlayer>
) {
    val size: Int get() = bySleeperId.size

    val sleeperIds: Set<String> get() = bySleeperId.keys

    val players: Collection<WeeklyPlayer> get() = bySleeperId.values

    operator fun get(sleeperId: String): WeeklyPlayer? = bySleeperId[sleeperId]

    /**
     * Projects a rostered player, optionally overriding features with fresher
     * values than the bundle was built with.
     *
     * The override is what makes Sunday-morning news actionable: the injury report
     * baked into the bundle is a day or two stale, so the app passes the live
     * Sleeper designation through [overrides] (keyed by feature name) and gets a
     * re-scored projection without waiting for a new bundle.
     */
    fun project(
        model: PredictionModel,
        sleeperId: String,
        injuryStatus: InjuryStatus,
        practice: PracticeParticipation? = null,
        overrides: Map<String, Double> = emptyMap()
    ): Projection? {
        val player = bySleeperId[sleeperId] ?: return null
        val features = if (overrides.isEmpty()) {
            player.features
        } else {
            player.features.copyOf().also { copy ->
                overrides.forEach { (name, value) ->
                    val index = model.featureNames.indexOf(name)
                    if (index >= 0) copy[index] = value
                }
            }
        }
        return model.project(player.position, features, injuryStatus, practice)
    }

    companion object {
        const val SUPPORTED_FORMAT_VERSION = 1

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * @throws IllegalArgumentException if the bundle was built against a
         * different feature list than [model] was trained on. Scoring positionally
         * across a mismatch would produce confident nonsense, so it fails loudly.
         */
        fun parse(text: String, model: PredictionModel): WeeklyBundle {
            val root = json.parseToJsonElement(text).jsonObject

            val version = root["format_version"]?.jsonPrimitive?.int
                ?: error("weekly bundle has no format_version")
            require(version == SUPPORTED_FORMAT_VERSION) {
                "weekly bundle is format v$version, this build reads v$SUPPORTED_FORMAT_VERSION"
            }

            val order = root.getValue("feature_order").jsonArray.map { it.jsonPrimitive.content }
            require(order == model.featureNames) {
                "weekly bundle features do not match the model: bundle has ${order.size}, " +
                    "model expects ${model.featureNames.size}. Rebuild both together."
            }

            val players = root.getValue("players").jsonArray.mapNotNull { element ->
                val obj = element.jsonObject
                // A position this build does not model (a kicker in a newer bundle)
                // is skipped rather than fatal.
                val position = Position.entries.firstOrNull {
                    it.name == obj.getValue("position").jsonPrimitive.content
                } ?: return@mapNotNull null
                val raw = obj.getValue("f") as JsonArray
                WeeklyPlayer(
                    sleeperId = obj.getValue("sleeper_id").jsonPrimitive.content,
                    name = obj.getValue("name").jsonPrimitive.content,
                    position = position,
                    team = obj.getValue("team").jsonPrimitive.content,
                    opponent = obj["opponent"]?.jsonPrimitive?.contentOrNull,
                    // A null here means the feature genuinely has no value, which
                    // the model handles; it must not become 0.0.
                    features = DoubleArray(raw.size) {
                        raw[it].jsonPrimitive.doubleOrNull ?: Double.NaN
                    }
                )
            }

            return WeeklyBundle(
                season = root.getValue("season").jsonPrimitive.int,
                week = root.getValue("week").jsonPrimitive.int,
                generated = root["generated"]?.jsonPrimitive?.content.orEmpty(),
                bySleeperId = players.associateBy { it.sleeperId }
            )
        }
    }
}
