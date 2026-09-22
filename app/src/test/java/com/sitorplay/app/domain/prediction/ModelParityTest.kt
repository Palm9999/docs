package com.sitorplay.app.domain.prediction

import com.sitorplay.app.domain.model.InjuryStatus
import com.sitorplay.app.domain.model.Position
import com.sitorplay.app.domain.model.PracticeParticipation
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the Kotlin tree walker reproduces LightGBM's own predictions exactly.
 *
 * This is the test that makes shipping a hand-written scorer defensible. The
 * fixtures in `model_fixtures.json` are real feature rows scored by Python at
 * export time; half of them deliberately contain missing features, because NaN
 * routing is where an independent reimplementation is most likely to drift while
 * still looking reasonable.
 *
 * Regenerate both files together with `python3 model/export_model.py`.
 */
class ModelParityTest {

    private val model: PredictionModel by lazy {
        val gz = File("src/main/assets/model.json.gz")
        assertTrue("missing ${gz.path} -- run model/export_model.py", gz.exists())
        ModelBundleParser.parse(
            GZIPInputStream(gz.inputStream()).bufferedReader().use { it.readText() }
        )
    }

    private val fixtures by lazy {
        val text = javaClass.classLoader!!
            .getResourceAsStream("model_fixtures.json")!!
            .bufferedReader().use { it.readText() }
        Json.parseToJsonElement(text).jsonObject
    }

    @Test
    fun `matches python predictions on every fixture`() {
        val cases = fixtures.getValue("cases").jsonArray
        assertTrue("no fixtures loaded", cases.size >= 20)

        var worst = 0.0
        var withMissing = 0
        cases.forEach { element ->
            val case = element.jsonObject
            val position = Position.valueOf(case.getValue("position").jsonPrimitive.content)
            val raw = case.getValue("features").jsonArray
            val features = DoubleArray(raw.size) {
                raw[it].jsonPrimitive.doubleOrNull ?: Double.NaN
            }
            if (features.any { it.isNaN() }) withMissing++

            val actual = model.rawRangeFor(position, features)
            assertNotNull("no model for $position", actual)

            val expected = case.getValue("expected").jsonObject
            listOf(
                "15" to actual!!.floor,
                "50" to actual.median,
                "85" to actual.ceiling
            ).forEach { (quantile, value) ->
                val want = expected.getValue(quantile).jsonPrimitive.double
                worst = maxOf(worst, kotlin.math.abs(want - value))
                assertEquals("p$quantile for $position", want, value, TOLERANCE)
            }
        }
        assertTrue("fixtures should cover missing features", withMissing > 0)
        println("parity: ${cases.size} cases ($withMissing with missing features), worst delta $worst")
    }

    @Test
    fun `feature order is carried by the bundle, not assumed`() {
        assertEquals(67, model.featureNames.size)
        assertTrue(model.featureNames.contains("implied_total"))
        assertTrue(model.featureNames.contains("vacated_target_share"))

        // A vector built from names must equal one built positionally.
        val byName = Features.vector(model, mapOf("implied_total" to 24.5))
        assertEquals(24.5, byName[model.featureNames.indexOf("implied_total")], 0.0)
        assertTrue("unsupplied features must stay NaN, never zero", byName[0].isNaN())
    }

    @Test
    fun `calibration preserves the median and the quantile ordering`() {
        val features = DoubleArray(model.featureNames.size) { Double.NaN }
        Position.entries.filter { model.supports(it) }.forEach { position ->
            val raw = model.rawRangeFor(position, features)!!
            val calibrated = model.rangeFor(position, features)!!
            assertEquals(raw.median, calibrated.median, 1e-9)
            assertTrue("$position ordering", calibrated.floor <= calibrated.median)
            assertTrue("$position ordering", calibrated.median <= calibrated.ceiling)
        }
    }

    @Test
    fun `play probability splits questionable by practice participation`() {
        val rates = model.playRates
        val full = rates.probability(InjuryStatus.QUESTIONABLE, PracticeParticipation.FULL)
        val limited = rates.probability(InjuryStatus.QUESTIONABLE, PracticeParticipation.LIMITED)
        val dnp = rates.probability(InjuryStatus.QUESTIONABLE, PracticeParticipation.DID_NOT_PRACTICE)

        assertTrue("full practice should beat DNP: $full vs $dnp", full > dnp)
        assertTrue("limited should sit between: $limited", limited in dnp..full)
        // The old flat multiplier; the whole point is that reality is well below it.
        assertTrue("questionable is far less likely to play than 0.85", full < 0.85)

        assertEquals(0.0, rates.probability(InjuryStatus.OUT, PracticeParticipation.FULL), 0.0)
        assertEquals(1.0, rates.probability(InjuryStatus.HEALTHY, null), 0.0)
    }

    @Test
    fun `expected points discount a player who may not suit up`() {
        val range = PointsRange(floor = 6.0, median = 14.0, ceiling = 22.0)
        val questionable = Projection(range, 0.6065)
        val healthy = Projection(PointsRange(5.0, 10.0, 16.0), 1.0)

        assertEquals(8.491, questionable.expectedPoints, 1e-3)
        assertTrue(
            "a 14-point questionable start should rank below a healthy 10-pointer",
            questionable.expectedPoints < healthy.expectedPoints
        )
    }

    private companion object {
        /** Sums of ~100 doubles; anything above this means a real divergence. */
        const val TOLERANCE = 1e-9
    }
}
