package com.sitorplay.app.data.prediction

import android.content.Context
import com.sitorplay.app.data.settings.AppSettingsRepository
import com.sitorplay.app.domain.model.InjuryStatus
import com.sitorplay.app.domain.model.Player
import com.sitorplay.app.domain.model.PracticeParticipation
import com.sitorplay.app.domain.prediction.FormStat
import com.sitorplay.app.domain.prediction.ModelBundleParser
import com.sitorplay.app.domain.prediction.PlayerForm
import com.sitorplay.app.domain.prediction.PredictionModel
import com.sitorplay.app.domain.prediction.Projection
import com.sitorplay.app.domain.prediction.Scenario
import com.sitorplay.app.domain.prediction.WeeklyPlayer
import com.sitorplay.app.domain.prediction.WeeklyBundle
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** What the app knows about its prediction model right now, for the settings screen. */
data class ModelStatus(
    val modelLoaded: Boolean = false,
    val trainedThrough: String = "",
    val scoring: String = "",
    val bundleSeason: Int? = null,
    val bundleWeek: Int? = null,
    val bundlePlayers: Int = 0,
    val bundleGenerated: String = "",
    val lastError: String? = null
) {
    val isReady: Boolean get() = modelLoaded && bundlePlayers > 0
}

/**
 * Owns the on-device model and the week of features it scores.
 *
 * The model itself ships in `assets/` and never changes without an app update.
 * The feature bundle changes weekly and is downloaded, because rebuilding it on
 * the phone would mean pulling a season of play-by-play. Both are optional: with
 * no bundle the app falls back to the projection-and-multiplier heuristic it
 * used before, so a failed download degrades the recommendations rather than
 * breaking the screen.
 */
@Singleton
class PredictionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val settings: AppSettingsRepository
) {

    private val loadLock = Mutex()
    private var model: PredictionModel? = null
    private var bundle: WeeklyBundle? = null

    private val _status = MutableStateFlow(ModelStatus())
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    private val cacheFile: File get() = File(context.filesDir, CACHE_NAME)

    /**
     * Loads the model and whatever bundle is on disk. Safe to call repeatedly;
     * the work happens once.
     */
    suspend fun ensureLoaded() = loadLock.withLock {
        if (model == null) {
            model = runCatching { readModelAsset() }
                .onFailure { failure -> recordError("Could not read the bundled model: ${failure.message}") }
                .getOrNull()
        }
        val loaded = model
        if (loaded != null && bundle == null && cacheFile.exists()) {
            bundle = runCatching { WeeklyBundle.parse(readGzip(cacheFile), loaded) }
                .onFailure { failure ->
                    // A cached bundle from an older model is worse than none: it
                    // would either fail the feature check or score nonsense.
                    cacheFile.delete()
                    recordError("Cached week was unusable and has been discarded: ${failure.message}")
                }
                .getOrNull()
        }
        publishStatus()
    }

    /**
     * Downloads this week's features. Returns false and leaves the previous
     * bundle in place if anything goes wrong.
     */
    suspend fun refreshWeeklyBundle(): Boolean {
        ensureLoaded()
        val loaded = model ?: return false
        val url = settings.modelBundleUrl.value
        if (url.isBlank()) {
            recordError("No weekly bundle URL is configured")
            return false
        }

        return try {
            val body = withContext(Dispatchers.IO) {
                client.newCall(buildRequest(url)).execute().use { response ->
                    if (!response.isSuccessful) {
                        // Deliberately just the status: the URL may carry a
                        // token and this string is shown on screen.
                        throw IOException(describe(response.code))
                    }
                    response.body?.bytes() ?: throw IOException("empty response")
                }
            }
            // Parse before writing, so a corrupt download never replaces a good cache.
            val parsed = WeeklyBundle.parse(decode(body), loaded)
            withContext(Dispatchers.IO) { cacheFile.writeBytes(body) }
            loadLock.withLock {
                bundle = parsed
                publishStatus(clearError = true)
            }
            true
        } catch (failure: Exception) {
            recordError("Weekly update failed: ${failure.message}")
            false
        }
    }

    /**
     * The model's projection for a rostered player, or null when the model has
     * nothing to say -- no bundle, an unmodelled position, or a player it has
     * never seen. Callers fall back to the plain projection in that case rather
     * than being handed a number the model did not produce.
     */
    fun projectionFor(player: Player): Projection? {
        val loaded = model ?: return null
        val week = bundle ?: return null
        val sleeperId = player.externalId ?: return null
        return week.project(
            model = loaded,
            sleeperId = sleeperId,
            injuryStatus = player.injuryStatus,
            practice = player.practiceParticipation
        )
    }

    /** Same, for a player not on the roster -- the compare screen's search results. */
    fun projectionFor(
        sleeperId: String?,
        injuryStatus: InjuryStatus,
        practice: PracticeParticipation?
    ): Projection? {
        val loaded = model ?: return null
        val week = bundle ?: return null
        if (sleeperId == null) return null
        return week.project(loaded, sleeperId, injuryStatus, practice)
    }

    /**
     * A plain GET, plus a bearer token when one is configured.
     *
     * GitHub's contents endpoint returns JSON metadata with the file
     * base64-encoded inside unless asked for the raw bytes, so that Accept
     * header is set when the URL points there -- which is the practical way to
     * read a bundle out of a private repository.
     */
    private fun buildRequest(url: String): Request {
        val builder = Request.Builder().url(url)
        val token = settings.modelBundleToken.value
        if (token.isNotBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        if (url.startsWith(GITHUB_API_PREFIX)) {
            builder.header("Accept", "application/vnd.github.raw")
        }
        return builder.build()
    }

    private fun describe(code: Int): String = when (code) {
        401, 403 -> "HTTP $code — the bundle needs an access token, or the one set is not valid"
        404 -> "HTTP 404 — nothing at that URL, or the repository is private and no token is set"
        else -> "HTTP $code"
    }

    /**
     * The stat line behind a player's projection -- yards, touchdowns, targets
     * and share of the offence, over the last three games and the season.
     *
     * Empty without a bundle or for an unmodelled position, which is how the UI
     * knows to leave the section out rather than draw a table of dashes.
     */
    fun formFor(player: Player): List<FormStat> = formFor(player.externalId)

    /** Same, for a player not on the roster -- the compare screen's search results. */
    fun formFor(sleeperId: String?): List<FormStat> {
        val loaded = model ?: return emptyList()
        val week = bundle ?: return emptyList()
        if (sleeperId == null) return emptyList()
        val weekly = week[sleeperId] ?: return emptyList()
        return PlayerForm.of(weekly, loaded)
    }

    /**
     * Team-mates of a rostered player, for the what-if list, busiest first.
     * Empty without a bundle, which is how the UI knows to hide the section.
     */
    fun teammatesOf(sleeperId: String?): List<WeeklyPlayer> {
        val week = bundle ?: return emptyList()
        if (sleeperId == null) return emptyList()
        return week.teammatesOf(sleeperId, model)
    }

    /** As [projectionFor], but with some team-mates moved in or out of the lineup. */
    fun projectionFor(player: Player, scenario: Scenario): Projection? {
        val loaded = model ?: return null
        val week = bundle ?: return null
        val sleeperId = player.externalId ?: return null
        return week.projectScenario(
            model = loaded,
            sleeperId = sleeperId,
            injuryStatus = player.injuryStatus,
            practice = player.practiceParticipation,
            scenario = scenario
        )
    }

    /**
     * Reads the model that ships inside the app.
     *
     * It is committed as `model.json.gz`, but that is not the name it has on a
     * device. aapt reads a gzipped asset as a request to store it deflated: it
     * strips the `.gz` and lets AssetManager inflate it on the way out, so the
     * APK holds `assets/model.json` as plain text. Rather than hard-code
     * whichever of those the current build tools happen to produce, take either
     * name and sniff the encoding. Getting it wrong throws into the runCatching
     * in ensureLoaded, leaves the model null, and turns every projection in the
     * app off without crashing or logging anything a user would notice.
     */
    private fun readModelAsset(): PredictionModel {
        val bytes = MODEL_ASSETS.firstNotNullOfOrNull { name ->
            runCatching { context.assets.open(name).use { it.readBytes() } }.getOrNull()
        } ?: throw IOException("No model in assets; looked for ${MODEL_ASSETS.joinToString()}")
        return ModelBundleParser.parse(
            if (isGzip(bytes)) decode(bytes) else bytes.decodeToString()
        )
    }

    /** Gzip's two magic bytes, which say whether [decode] is needed. */
    private fun isGzip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()

    private fun readGzip(file: File): String =
        GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() }

    private fun decode(bytes: ByteArray): String =
        GZIPInputStream(bytes.inputStream()).bufferedReader().use { it.readText() }

    private fun recordError(message: String) {
        _status.value = _status.value.copy(lastError = message)
    }

    private fun publishStatus(clearError: Boolean = false) {
        val loaded = model
        val week = bundle
        _status.value = ModelStatus(
            modelLoaded = loaded != null,
            trainedThrough = loaded?.trainedThrough.orEmpty(),
            scoring = loaded?.scoring.orEmpty(),
            bundleSeason = week?.season,
            bundleWeek = week?.week,
            bundlePlayers = week?.size ?: 0,
            bundleGenerated = week?.generated.orEmpty(),
            lastError = if (clearError) null else _status.value.lastError
        )
    }

    private companion object {
        // Both spellings, because which one is in the APK is the packaging
        // tool's decision, not ours. Kept in sync with tools/check_apk.py,
        // which reads this list and asserts the APK actually contains one.
        val MODEL_ASSETS = listOf("model.json", "model.json.gz")
        const val CACHE_NAME = "week_features.json.gz"
        const val GITHUB_API_PREFIX = "https://api.github.com/"
    }
}
