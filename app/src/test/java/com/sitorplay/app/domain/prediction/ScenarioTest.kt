package com.sitorplay.app.domain.prediction

import com.sitorplay.app.domain.model.InjuryStatus
import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the what-if: "how does this player's week change if a team-mate sits?"
 *
 * Run against the real committed bundle rather than a fixture, because the
 * behaviour depends on which players share a team and who the bundle already
 * has as out — structure a handmade fixture would have to fake.
 */
class ScenarioTest {

    private val model: PredictionModel by lazy {
        ModelBundleParser.parse(readGzip(File("src/main/assets/model.json.gz")))
    }

    private val bundle: WeeklyBundle by lazy {
        val stream = javaClass.classLoader!!.getResourceAsStream("week_sample.json.gz")!!
        WeeklyBundle.parse(
            GZIPInputStream(stream).bufferedReader().use { it.readText() }, model
        )
    }

    private fun readGzip(file: File): String =
        GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() }

    /** A receiver with team-mates who carry real target share, so a change shows. */
    private fun busyReceiver(): WeeklyPlayer {
        val targetShare = model.featureNames.indexOf("target_share_r3")
        return bundle.players
            .filter { it.position == com.sitorplay.app.domain.model.Position.WR && !it.ruledOut }
            .filter { player ->
                bundle.teammatesOf(player.sleeperId).count {
                    !it.ruledOut && it.features[targetShare] > 0.15
                } > 0
            }
            .maxByOrNull { it.features[targetShare] }
            ?: error("no suitable receiver in the bundle")
    }

    @Test
    fun `ruling out a team-mate raises the players projection`() {
        val player = busyReceiver()
        val targetShare = model.featureNames.indexOf("target_share_r3")
        val biggest = bundle.teammatesOf(player.sleeperId)
            .filter { !it.ruledOut }
            .maxByOrNull { it.features[targetShare] }!!

        val before = bundle.project(model, player.sleeperId, InjuryStatus.HEALTHY)!!
        val after = bundle.projectScenario(
            model, player.sleeperId, InjuryStatus.HEALTHY,
            scenario = Scenario(ruledOut = setOf(biggest.sleeperId))
        )!!

        // Guaranteed, not hoped for: the trees alone do not deliver this, so
        // projectScenario imposes the direction. See its comment for why.
        assertTrue(
            "${player.name} should not drop when ${biggest.name} sits: " +
                "${before.range.median} -> ${after.range.median}",
            after.range.median >= before.range.median
        )
        assertTrue("floor must not drop either", after.range.floor >= before.range.floor)
        assertTrue("ordering", after.range.floor <= after.range.median)
        println(
            "${player.name}: ${"%.2f".format(before.range.median)} -> " +
                "${"%.2f".format(after.range.median)} with ${biggest.name} out"
        )
    }

    @Test
    fun `no team-mate absence ever lowers a projection`() {
        val targetShare = model.featureNames.indexOf("target_share_r3")
        var checked = 0
        bundle.players.filter { !it.ruledOut }.take(60).forEach { player ->
            val teammate = bundle.teammatesOf(player.sleeperId)
                .filter { !it.ruledOut && it.features[targetShare] > 0.05 }
                .maxByOrNull { it.features[targetShare] } ?: return@forEach

            val before = bundle.project(model, player.sleeperId, InjuryStatus.HEALTHY)!!
            val after = bundle.projectScenario(
                model, player.sleeperId, InjuryStatus.HEALTHY,
                scenario = Scenario(ruledOut = setOf(teammate.sleeperId))
            )!!
            assertTrue(
                "${player.name} dropped when ${teammate.name} was ruled out",
                after.range.median >= before.range.median
            )
            checked++
        }
        assertTrue("expected to check a meaningful number, got $checked", checked > 20)
        println("monotonicity held across $checked player/team-mate pairs")
    }

    @Test
    fun `an empty scenario is exactly the baseline`() {
        val player = busyReceiver()
        val baseline = bundle.project(model, player.sleeperId, InjuryStatus.HEALTHY)!!
        val empty = bundle.projectScenario(
            model, player.sleeperId, InjuryStatus.HEALTHY, scenario = Scenario()
        )!!
        assertEquals(baseline.range.median, empty.range.median, 0.0)
        assertEquals(baseline.range.floor, empty.range.floor, 0.0)
    }

    @Test
    fun `a player already out is not counted twice`() {
        val alreadyOut = bundle.players.firstOrNull { it.ruledOut }
        if (alreadyOut == null) {
            println("no sidelined players in this bundle; nothing to double-count")
            return
        }
        val teammate = bundle.teammatesOf(alreadyOut.sleeperId).firstOrNull { !it.ruledOut }
            ?: return

        val baseline = bundle.project(model, teammate.sleeperId, InjuryStatus.HEALTHY)!!
        val redundant = bundle.projectScenario(
            model, teammate.sleeperId, InjuryStatus.HEALTHY,
            scenario = Scenario(ruledOut = setOf(alreadyOut.sleeperId))
        )!!
        assertEquals(
            "ruling out a player the bundle already has out must change nothing",
            baseline.range.median, redundant.range.median, 0.0
        )
    }

    @Test
    fun `players on other teams do not affect each other`() {
        val player = busyReceiver()
        val stranger = bundle.players.first { it.team != player.team }

        val baseline = bundle.project(model, player.sleeperId, InjuryStatus.HEALTHY)!!
        val unaffected = bundle.projectScenario(
            model, player.sleeperId, InjuryStatus.HEALTHY,
            scenario = Scenario(ruledOut = setOf(stranger.sleeperId))
        )!!
        assertEquals(baseline.range.median, unaffected.range.median, 0.0)
    }

    @Test
    fun `toggling walks out, back, and to neutral`() {
        var scenario = Scenario()

        // A healthy player: first toggle rules him out, second undoes it.
        scenario = scenario.toggle("healthy", wasRuledOut = false)
        assertTrue(scenario.isOut("healthy", wasRuledOut = false))
        scenario = scenario.toggle("healthy", wasRuledOut = false)
        assertTrue(scenario.isEmpty)

        // A player the bundle has out: first toggle clears him to play.
        scenario = scenario.toggle("injured", wasRuledOut = true)
        assertTrue(!scenario.isOut("injured", wasRuledOut = true))
        scenario = scenario.toggle("injured", wasRuledOut = true)
        assertTrue(scenario.isEmpty)
    }

    @Test
    fun `teammates are this team only, and never the player themselves`() {
        val player = busyReceiver()
        val teammates = bundle.teammatesOf(player.sleeperId)

        assertTrue("expected team-mates for ${player.team}", teammates.isNotEmpty())
        assertTrue(teammates.all { it.team == player.team })
        assertTrue(teammates.none { it.sleeperId == player.sleeperId })
    }

    @Test
    fun `team-mates come back busiest first when the model is supplied`() {
        val player = busyReceiver()
        val targetShare = model.featureNames.indexOf("target_share_r3")
        val carryShare = model.featureNames.indexOf("carry_share_r3")

        fun usage(p: WeeklyPlayer): Double {
            val t = p.features[targetShare].let { if (it.isNaN()) 0.0 else it }
            val c = p.features[carryShare].let { if (it.isNaN()) 0.0 else it }
            return t + c
        }

        val ordered = bundle.teammatesOf(player.sleeperId, model)
        assertTrue("expected team-mates", ordered.size > 3)
        ordered.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "${a.name} (${usage(a)}) should not rank below ${b.name} (${usage(b)})",
                usage(a) >= usage(b) - 1e-9
            )
        }
        // The point of the ordering: whoever matters most is reachable without
        // scrolling past a dozen backups.
        assertTrue("busiest team-mate should lead", usage(ordered.first()) > 0.0)
    }

    @Test
    fun `without a model the ordering is still stable`() {
        val player = busyReceiver()
        val first = bundle.teammatesOf(player.sleeperId)
        val second = bundle.teammatesOf(player.sleeperId)
        assertEquals(first.map { it.sleeperId }, second.map { it.sleeperId })
    }

    @Test
    fun `an unknown player yields nothing rather than a guess`() {
        assertNull(
            bundle.projectScenario(
                model, "not-a-player", InjuryStatus.HEALTHY,
                scenario = Scenario(ruledOut = setOf("also-not-a-player"))
            )
        )
        assertTrue(bundle.teammatesOf("not-a-player").isEmpty())
        assertNotNull(bundle.project(model, busyReceiver().sleeperId, InjuryStatus.HEALTHY))
    }
}
