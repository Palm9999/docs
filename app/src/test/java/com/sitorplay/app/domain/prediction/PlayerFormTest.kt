package com.sitorplay.app.domain.prediction

import com.sitorplay.app.domain.model.Position
import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the stat line shown behind a projection.
 *
 * These numbers are read straight out of the feature vector, so the risks are
 * all in the reading: a wrong index shows one player another's yards, a share
 * left as a fraction reads as "0.2", and a missing history reaches the screen as
 * "NaN". Each of those is checked against the real model and a real week rather
 * than a fixture, because the mapping being tested is to the actual feature
 * order and nothing else would exercise it.
 */
class PlayerFormTest {

    private val model: PredictionModel by lazy {
        ModelBundleParser.parse(readGzip(File("src/main/assets/model.json.gz")))
    }

    private val bundle: WeeklyBundle by lazy {
        val text = javaClass.classLoader!!.getResourceAsStream("week_sample.json.gz")!!
            .let { GZIPInputStream(it).bufferedReader().use { reader -> reader.readText() } }
        WeeklyBundle.parse(text, model)
    }

    private fun readGzip(file: File): String {
        assertTrue("missing ${file.path} -- run model/export_model.py", file.exists())
        return GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() }
    }

    private fun anyWith(position: Position): WeeklyPlayer =
        requireNotNull(bundle.players.firstOrNull { it.position == position }) {
            "no $position in the sample week"
        }

    @Test
    fun `every modelled player gets a stat line`() {
        var covered = 0
        bundle.players.forEach { player ->
            val form = PlayerForm.of(player, model)
            if (player.position in setOf(Position.QB, Position.RB, Position.WR, Position.TE)) {
                assertTrue("empty form for ${player.name}", form.isNotEmpty())
                covered++
            }
        }
        assertTrue("nothing covered", covered > 300)
    }

    @Test
    fun `each position shows the stats that decide its scoring`() {
        val labels = { p: Position -> PlayerForm.of(anyWith(p), model).map { it.label } }

        assertTrue("a passer needs passing yards", "Passing yards" in labels(Position.QB))
        assertTrue("a back needs carries", "Carries" in labels(Position.RB))
        assertTrue("a receiver needs targets", "Targets" in labels(Position.WR))
        assertTrue("a receiver needs target share", "Target share" in labels(Position.WR))
        // Touchdowns decide more weeks than yardage does, so no position omits them.
        Position.entries.filter { it != Position.K && it != Position.DEF }.forEach {
            assertTrue("$it is missing touchdowns", "Touchdowns" in labels(it))
        }
    }

    @Test
    fun `unmodelled positions produce nothing rather than a table of dashes`() {
        val kicker = anyWith(Position.WR).let {
            WeeklyPlayer(
                sleeperId = it.sleeperId,
                name = it.name,
                position = Position.K,
                team = it.team,
                opponent = it.opponent,
                ruledOut = it.ruledOut,
                features = it.features
            )
        }
        assertTrue(PlayerForm.of(kicker, model).isEmpty())
    }

    @Test
    fun `numbers land in the range football actually produces`() {
        bundle.players.forEach { player ->
            PlayerForm.of(player, model).forEach { stat ->
                listOfNotNull(stat.lastThree, stat.season).forEach { value ->
                    when (stat.unit) {
                        // A share above 1 would mean more than the whole offence.
                        StatUnit.SHARE ->
                            assertTrue("${player.name} ${stat.label} = $value", value <= 1.01)
                        // Per-game yardage; nobody averages 400.
                        StatUnit.YARDS ->
                            assertTrue("${player.name} ${stat.label} = $value", value < 400)
                        StatUnit.COUNT ->
                            assertTrue("${player.name} ${stat.label} = $value", value < 60)
                    }
                }
            }
        }
    }

    @Test
    fun `a share reads as a percentage and yardage as whole yards`() {
        assertEquals("24%", StatUnit.SHARE.format(0.238))
        assertEquals("0%", StatUnit.SHARE.format(0.0))
        assertEquals("82", StatUnit.YARDS.format(81.6))
        assertEquals("0.7", StatUnit.COUNT.format(0.66))
    }

    @Test
    fun `a missing history reads as a dash, never as NaN`() {
        assertEquals("--", StatUnit.COUNT.format(null))
        assertEquals("--", StatUnit.YARDS.format(null))
        assertEquals("--", StatUnit.SHARE.format(null))

        val blank = anyWith(Position.WR).let {
            WeeklyPlayer(
                sleeperId = it.sleeperId,
                name = it.name,
                position = it.position,
                team = it.team,
                opponent = it.opponent,
                ruledOut = it.ruledOut,
                features = DoubleArray(it.features.size) { Double.NaN }
            )
        }
        // Every value is absent, so every row drops out and the section is hidden
        // rather than drawn empty.
        assertTrue(PlayerForm.of(blank, model).isEmpty())
    }

    @Test
    fun `two players at the same position line up row for row`() {
        val receivers = bundle.players.filter { it.position == Position.WR }.take(2)
        val a = PlayerForm.of(receivers[0], model)
        val b = PlayerForm.of(receivers[1], model)

        val rows = PlayerForm.align(a, b)
        assertEquals(a.map { it.label }, rows.map { it.label })
        assertTrue("both sides should be filled", rows.all { it.left != null && it.right != null })
        assertTrue(rows.none { it.leftText == "--" || it.rightText == "--" })
    }

    @Test
    fun `a flex comparison keeps the stats only one of them has`() {
        val back = PlayerForm.of(anyWith(Position.RB), model)
        val receiver = PlayerForm.of(anyWith(Position.WR), model)

        val rows = PlayerForm.align(back, receiver)
        val carries = rows.single { it.label == "Carries" }
        assertNotNull("the back carries the ball", carries.left)
        // A receiver has no carry line, and saying so is the useful answer.
        assertEquals("--", carries.rightText)

        val receptions = rows.single { it.label == "Receptions" }
        assertEquals("--", receptions.leftText)
        assertNotNull(receptions.right)

        // Nothing is lost from either side.
        assertTrue((back.map { it.label } + receiver.map { it.label }).toSet() ==
            rows.map { it.label }.toSet())
    }

    @Test
    fun `an empty slot still shows the other player's line`() {
        val receiver = PlayerForm.of(anyWith(Position.WR), model)
        assertEquals(receiver.size, PlayerForm.align(receiver, emptyList()).size)
        assertEquals(receiver.size, PlayerForm.align(emptyList(), receiver).size)
        assertTrue(PlayerForm.align(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `the stat line belongs to the player it was asked about`() {
        // Two different players must not produce identical lines; an index bug
        // that read a fixed offset would.
        val receivers = bundle.players.filter { it.position == Position.WR }.take(40)
        val lines = receivers.map { player ->
            PlayerForm.of(player, model).joinToString { "${it.label}=${it.lastThreeText}" }
        }
        assertNotNull(lines)
        assertTrue("all 40 receivers produced the same stat line", lines.toSet().size > 1)
    }
}
