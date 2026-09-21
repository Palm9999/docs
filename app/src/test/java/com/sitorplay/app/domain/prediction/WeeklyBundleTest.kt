package com.sitorplay.app.domain.prediction

import com.sitorplay.app.domain.model.InjuryStatus
import com.sitorplay.app.domain.model.PracticeParticipation
import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the weekly feature bundle: that it lines up with the model it will be
 * scored against, and that scoring a real week produces sane football numbers.
 *
 * Regenerate `week_sample.json.gz` with `python3 model/build_week.py`.
 */
class WeeklyBundleTest {

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

    @Test
    fun `bundle loads and covers a full slate`() {
        assertTrue("expected a few hundred players, got ${bundle.size}", bundle.size > 300)
        assertTrue(bundle.week in 1..18)
    }

    @Test
    fun `every player scores to a plausible range`() {
        var scored = 0
        bundle.sleeperIds.forEach { id ->
            val projection = bundle.project(model, id, InjuryStatus.HEALTHY) ?: return@forEach
            scored++
            val range = projection.range
            // A floor may be slightly negative for a marginal player; what must
            // always hold is the ordering.
            assertTrue("ordering broken for $id", range.floor <= range.median)
            assertTrue("floor implausibly low for $id", range.floor > -10.0)
            assertTrue("ordering broken for $id", range.median <= range.ceiling)
            assertTrue("implausible median ${range.median} for $id", range.median < 60.0)
        }
        assertTrue("nothing scored", scored > 300)
    }

    @Test
    fun `a mismatched bundle is rejected rather than scored positionally`() {
        val shifted = PredictionModel(
            featureNames = model.featureNames.drop(1),
            scoring = model.scoring,
            trainedThrough = model.trainedThrough,
            positions = emptyMap(),
            playRates = PlayRates.DEFAULT
        )
        val text = javaClass.classLoader!!.getResourceAsStream("week_sample.json.gz")!!
            .let { GZIPInputStream(it).bufferedReader().use { reader -> reader.readText() } }

        val failure = runCatching { WeeklyBundle.parse(text, shifted) }.exceptionOrNull()
        assertNotNull("a feature mismatch must throw", failure)
        assertTrue(failure!!.message!!.contains("do not match the model"))
    }

    @Test
    fun `a live injury override re-scores without a new bundle`() {
        val id = bundle.sleeperIds.first()
        val healthy = bundle.project(model, id, InjuryStatus.HEALTHY)!!
        val questionable = bundle.project(
            model, id, InjuryStatus.QUESTIONABLE, PracticeParticipation.DID_NOT_PRACTICE
        )!!

        // Same conditional range -- the model does not change -- but the expected
        // value drops, because the odds of playing do.
        assertEquals(healthy.range.median, questionable.range.median, 1e-9)
        assertTrue(questionable.expectedPoints < healthy.expectedPoints)
        assertEquals(1.0, healthy.playProbability, 1e-9)

        // Overriding a feature changes the range itself.
        val shifted = bundle.project(
            model, id, InjuryStatus.HEALTHY, overrides = mapOf("implied_total" to 5.0)
        )!!
        assertTrue(
            "a 5-point implied team total should not project the same as the real one",
            shifted.range.median != healthy.range.median
        )
    }

    @Test
    fun `unknown players return null instead of a guess`() {
        assertNull(bundle.project(model, "definitely-not-a-player", InjuryStatus.HEALTHY))
        assertNull(bundle["definitely-not-a-player"])
    }
}
