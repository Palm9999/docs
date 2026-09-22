package com.sitorplay.app.domain.prediction

import com.sitorplay.app.domain.model.Position
import kotlin.math.roundToInt

/**
 * How a number should read once it reaches a label.
 *
 * The bundle stores shares as fractions, so anything a viewer would expect as a
 * percentage has to be scaled here rather than at the call site -- getting that
 * wrong shows a 24% target share as "0.2".
 */
enum class StatUnit {
    /** Whole yards. A tenth of a yard per game is noise, not information. */
    YARDS,

    /** Counting stats -- targets, carries, touchdowns -- to one decimal. */
    COUNT,

    /** A fraction of the team's work, shown as a percentage. */
    SHARE;

    fun format(value: Double?): String = when {
        value == null -> "--"
        this == YARDS -> value.roundToInt().toString()
        this == SHARE -> "${(value * 100).roundToInt()}%"
        else -> String.format("%.1f", value)
    }
}

/**
 * One statistic as the player has actually been producing it, per game.
 *
 * Both windows are carried because either alone misleads: the three-game average
 * catches a change in role but overreacts to one blowout, and the season average
 * is steadier but keeps counting weeks in which the player had a different job.
 * Seeing them side by side is the point.
 */
data class FormStat(
    val label: String,
    val lastThree: Double?,
    val season: Double?,
    val unit: StatUnit
) {
    val lastThreeText: String get() = unit.format(lastThree)
    val seasonText: String get() = unit.format(season)
}

/**
 * One row of a two-player comparison.
 *
 * Either side may be absent: comparing a back with a receiver, as a flex slot
 * forces you to, means "Carries" exists for one of them and not the other. The
 * row is kept with a blank on the missing side rather than dropped, because the
 * absence is itself the answer -- a receiver does not carry the ball.
 */
data class FormComparison(
    val label: String,
    val left: FormStat?,
    val right: FormStat?
) {
    val leftText: String get() = left?.lastThreeText ?: "--"
    val rightText: String get() = right?.lastThreeText ?: "--"
}

/**
 * The stat line behind a projection, read out of the feature vector the app
 * already holds.
 *
 * Nothing here is predicted. These are the same rolled averages the model scores
 * on, which is precisely why they are worth showing: they are the evidence the
 * projection was built from, so a user who disagrees with a number can see what
 * drove it instead of being handed a total to take on faith.
 */
object PlayerForm {

    private data class Spec(val label: String, val stat: String, val unit: StatUnit)

    // Touchdowns are combined -- passing, rushing and receiving -- because that
    // is how the feature is built. For a quarterback it is very nearly passing
    // touchdowns; for everyone else it is the number that matters anyway.
    private val PASSER = listOf(
        Spec("Passing yards", "passing_yards", StatUnit.YARDS),
        Spec("Rushing yards", "rushing_yards", StatUnit.YARDS),
        Spec("Touchdowns", "total_tds", StatUnit.COUNT),
        Spec("Fantasy points", "fp", StatUnit.COUNT)
    )

    private val RUSHER = listOf(
        Spec("Carries", "carries", StatUnit.COUNT),
        Spec("Rushing yards", "rushing_yards", StatUnit.YARDS),
        Spec("Targets", "targets", StatUnit.COUNT),
        Spec("Receiving yards", "receiving_yards", StatUnit.YARDS),
        Spec("Touchdowns", "total_tds", StatUnit.COUNT),
        Spec("Carry share", "carry_share", StatUnit.SHARE),
        Spec("Snap share", "snap_pct", StatUnit.SHARE),
        Spec("Fantasy points", "fp", StatUnit.COUNT)
    )

    private val RECEIVER = listOf(
        Spec("Targets", "targets", StatUnit.COUNT),
        Spec("Receptions", "receptions", StatUnit.COUNT),
        Spec("Receiving yards", "receiving_yards", StatUnit.YARDS),
        Spec("Touchdowns", "total_tds", StatUnit.COUNT),
        Spec("Target share", "target_share", StatUnit.SHARE),
        Spec("Snap share", "snap_pct", StatUnit.SHARE),
        Spec("Fantasy points", "fp", StatUnit.COUNT)
    )

    /** Feature suffixes: last three games, and this season to date. */
    private const val RECENT = "_r3"
    private const val SEASON = "_std"

    /**
     * The stat line for a player, or empty if the model carries none of it.
     *
     * Empty rather than a list of dashes: a panel of "--" is worse than no panel,
     * and the caller uses emptiness to decide whether to draw the section at all.
     */
    fun of(player: WeeklyPlayer, model: PredictionModel): List<FormStat> {
        val specs = when (player.position) {
            Position.QB -> PASSER
            Position.RB -> RUSHER
            Position.WR, Position.TE -> RECEIVER
            // Kickers and defences are not in the model, so there is no vector
            // to read and nothing honest to put on the screen.
            Position.K, Position.DEF -> return emptyList()
        }

        val index = model.featureNames.withIndex().associate { (i, name) -> name to i }
        return specs.mapNotNull { spec ->
            val recent = player.featureOrNull(index[spec.stat + RECENT])
            val season = player.featureOrNull(index[spec.stat + SEASON])
            if (recent == null && season == null) null
            else FormStat(spec.label, recent, season, spec.unit)
        }
    }

    /**
     * Two stat lines laid out against each other, one row per statistic.
     *
     * The left player's rows come first and anything only the right player has
     * follows, so the common case -- two players at the same position -- reads
     * in the order the position defines, and the odd case still shows every
     * statistic rather than silently dropping half of one player's line.
     */
    fun align(left: List<FormStat>, right: List<FormStat>): List<FormComparison> {
        val byLabelLeft = left.associateBy { it.label }
        val byLabelRight = right.associateBy { it.label }
        val labels = left.map { it.label } + right.map { it.label }.filter { it !in byLabelLeft }
        return labels.map { FormComparison(it, byLabelLeft[it], byLabelRight[it]) }
    }

    /**
     * A feature by position, or null if it is absent or unknown.
     *
     * NaN is how the pipeline spells "no history", and it has to become null here
     * rather than reaching a formatter, which would render it as "NaN".
     */
    private fun WeeklyPlayer.featureOrNull(index: Int?): Double? {
        if (index == null || index < 0 || index >= features.size) return null
        val value = features[index]
        return if (value.isNaN()) null else value
    }
}
