package com.sitorplay.app.domain.prediction

import com.sitorplay.app.domain.model.InjuryStatus
import com.sitorplay.app.domain.model.Position
import com.sitorplay.app.domain.model.PracticeParticipation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
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
    /** Whether the bundle already counted this player as out when it was built. */
    val ruledOut: Boolean,
    val features: DoubleArray
) {
    // DoubleArray gives this class reference equality by default, which silently
    // breaks any set or map keyed on it.
    override fun equals(other: Any?): Boolean =
        this === other || (other is WeeklyPlayer && sleeperId == other.sleeperId)

    override fun hashCode(): Int = sleeperId.hashCode()
}

/**
 * Teammates the user has moved in or out of the lineup for a hypothetical.
 *
 * Both directions are needed: a player the bundle already has as out can be put
 * back ("he's been upgraded to probable"), which removes usage the model was
 * handing to everyone else.
 */
data class Scenario(
    val ruledOut: Set<String> = emptySet(),
    val clearedToPlay: Set<String> = emptySet()
) {
    val isEmpty: Boolean get() = ruledOut.isEmpty() && clearedToPlay.isEmpty()

    fun toggle(sleeperId: String, wasRuledOut: Boolean): Scenario = when {
        // Currently forced out by the scenario -> back to the bundle's view.
        sleeperId in ruledOut -> copy(ruledOut = ruledOut - sleeperId)
        sleeperId in clearedToPlay -> copy(clearedToPlay = clearedToPlay - sleeperId)
        wasRuledOut -> copy(clearedToPlay = clearedToPlay + sleeperId)
        else -> copy(ruledOut = ruledOut + sleeperId)
    }

    /** Whether this player is sitting once the scenario is applied. */
    fun isOut(sleeperId: String, wasRuledOut: Boolean): Boolean = when {
        sleeperId in ruledOut -> true
        sleeperId in clearedToPlay -> false
        else -> wasRuledOut
    }
}

/**
 * Where the features a scenario edits live in the model's vector.
 *
 * Resolved once by name rather than assumed by position, so a retrained model
 * that reorders or renames its features disables the what-if instead of
 * overwriting whichever feature happens to sit at that index.
 */
internal data class FeatureSlots(
    val vacatedTargets: Int,
    val vacatedCarries: Int,
    val targetShare: Int,
    val carryShare: Int
) {
    companion object {
        fun of(model: PredictionModel): FeatureSlots? {
            val slots = FeatureSlots(
                vacatedTargets = model.featureNames.indexOf("vacated_target_share"),
                vacatedCarries = model.featureNames.indexOf("vacated_carry_share"),
                targetShare = model.featureNames.indexOf("target_share_r3"),
                carryShare = model.featureNames.indexOf("carry_share_r3")
            )
            return slots.takeIf {
                it.vacatedTargets >= 0 && it.vacatedCarries >= 0 &&
                    it.targetShare >= 0 && it.carryShare >= 0
            }
        }
    }
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

    /**
     * Everyone on a player's NFL team, excluding the player themselves.
     *
     * Pass [model] to order them by how much of the offence they actually use.
     * A what-if list is only useful if the player whose absence would matter is
     * near the top; alphabetical order buries him among backups.
     */
    fun teammatesOf(sleeperId: String, model: PredictionModel? = null): List<WeeklyPlayer> {
        val player = bySleeperId[sleeperId] ?: return emptyList()
        val teammates = bySleeperId.values
            .filter { it.team == player.team && it.sleeperId != sleeperId }

        val slots = model?.let { FeatureSlots.of(it) }
            ?: return teammates.sortedWith(compareBy({ it.position.ordinal }, { it.name }))

        return teammates.sortedWith(
            compareByDescending<WeeklyPlayer> { it.usageShare(slots) }.thenBy { it.name }
        )
    }

    /** Share of the offence this player has been taking: targets plus carries. */
    private fun WeeklyPlayer.usageShare(slots: FeatureSlots): Double =
        features.at(slots.targetShare) + features.at(slots.carryShare)

    /**
     * Projects a player under a hypothetical set of teammate absences.
     *
     * "What happens to him if the WR1 sits?" is the question a projection cannot
     * answer and a user asks every Sunday morning. The model already takes
     * vacated target and carry share as features, so the answer is to recompute
     * those two and score again -- no new model, no round trip.
     *
     * The change is applied as a delta against the bundle's own figure rather
     * than recomputed from scratch, because the bundle counted absent players
     * the app cannot see: anyone whose Sleeper id could not be matched, or who
     * has too little history to be worth projecting, still vacated real usage.
     * Rebuilding the total from visible team-mates alone would quietly drop them.
     */
    fun projectScenario(
        model: PredictionModel,
        sleeperId: String,
        injuryStatus: InjuryStatus,
        practice: PracticeParticipation? = null,
        scenario: Scenario
    ): Projection? {
        val player = bySleeperId[sleeperId] ?: return null
        if (scenario.isEmpty) {
            return project(model, sleeperId, injuryStatus, practice)
        }

        val slots = FeatureSlots.of(model) ?: return project(model, sleeperId, injuryStatus, practice)
        var targetDelta = 0.0
        var carryDelta = 0.0

        scenario.ruledOut.forEach { id ->
            val teammate = bySleeperId[id] ?: return@forEach
            // Already counted, or not on this team: nothing to add.
            if (teammate.team != player.team || teammate.ruledOut) return@forEach
            targetDelta += teammate.features.at(slots.targetShare)
            carryDelta += teammate.features.at(slots.carryShare)
        }
        scenario.clearedToPlay.forEach { id ->
            val teammate = bySleeperId[id] ?: return@forEach
            if (teammate.team != player.team || !teammate.ruledOut) return@forEach
            targetDelta -= teammate.features.at(slots.targetShare)
            carryDelta -= teammate.features.at(slots.carryShare)
        }

        val overrides = mapOf(
            model.featureNames[slots.vacatedTargets] to
                (player.features.at(slots.vacatedTargets) + targetDelta).coerceAtLeast(0.0),
            model.featureNames[slots.vacatedCarries] to
                (player.features.at(slots.vacatedCarries) + carryDelta).coerceAtLeast(0.0)
        )
        val scenarioProjection = project(model, sleeperId, injuryStatus, practice, overrides)
            ?: return null
        val baseline = project(model, sleeperId, injuryStatus, practice) ?: return null

        // Vacated usage can only help, which is football rather than fit. The
        // trees do not know it: on a feature this weak they move either way, and
        // in practice a receiver's projection can dip slightly when a team-mate
        // is ruled out. LightGBM refuses monotone constraints on a quantile
        // objective, so the direction is imposed here instead. It only ever
        // moves a result back toward the baseline, never past it, so the model
        // still decides the size of the change -- this decides the sign.
        return when {
            targetDelta + carryDelta > 0.0 -> scenarioProjection.atLeast(baseline)
            targetDelta + carryDelta < 0.0 -> scenarioProjection.atMost(baseline)
            else -> scenarioProjection
        }
    }

    /** Per-quantile max. Ordering survives: max of two ordered triples is ordered. */
    private fun Projection.atLeast(floorProjection: Projection) = Projection(
        PointsRange(
            floor = maxOf(range.floor, floorProjection.range.floor),
            median = maxOf(range.median, floorProjection.range.median),
            ceiling = maxOf(range.ceiling, floorProjection.range.ceiling)
        ),
        playProbability
    )

    private fun Projection.atMost(capProjection: Projection) = Projection(
        PointsRange(
            floor = minOf(range.floor, capProjection.range.floor),
            median = minOf(range.median, capProjection.range.median),
            ceiling = minOf(range.ceiling, capProjection.range.ceiling)
        ),
        playProbability
    )

    /** NaN means "unknown", which contributes nothing to a share that is summed. */
    private fun DoubleArray.at(index: Int): Double =
        getOrNull(index)?.takeUnless { it.isNaN() } ?: 0.0

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
                    ruledOut = obj["out"]?.jsonPrimitive?.booleanOrNull ?: false,
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
