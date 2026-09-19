package com.sitorplay.app.data.sync

import android.content.SharedPreferences
import androidx.core.content.edit
import com.sitorplay.app.data.local.NflPlayerDao
import com.sitorplay.app.data.local.NflPlayerEntity
import com.sitorplay.app.data.local.toDomain
import com.sitorplay.app.data.remote.EspnApi
import com.sitorplay.app.data.remote.SleeperApi
import com.sitorplay.app.data.remote.dto.SleeperProjectionDto
import com.sitorplay.app.data.settings.AppSettingsRepository
import com.sitorplay.app.data.settings.ScoringFormat
import com.sitorplay.app.domain.model.BYE_WEEK_OPPONENT
import com.sitorplay.app.domain.model.InjuryStatus
import com.sitorplay.app.domain.model.NflPlayer
import com.sitorplay.app.domain.model.Player
import com.sitorplay.app.domain.model.PlayerDetailExtras
import com.sitorplay.app.domain.model.Position
import com.sitorplay.app.domain.model.TrendDirection
import com.sitorplay.app.domain.model.TrendInfo
import com.sitorplay.app.domain.model.WaiverSuggestion
import com.sitorplay.app.domain.model.WeeklyContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val FANTASY_POSITIONS = setOf("QB", "RB", "WR", "TE", "K", "DEF")
private const val DIRECTORY_MAX_AGE_MS = 24L * 60 * 60 * 1000
private const val LAST_SYNCED_KEY = "nfl_players_synced_at"

@Singleton
class NflDataRepository @Inject constructor(
    private val sleeperApi: SleeperApi,
    private val espnApi: EspnApi,
    private val nflPlayerDao: NflPlayerDao,
    private val preferences: SharedPreferences,
    private val appSettingsRepository: AppSettingsRepository
) {
    /** In-memory only: this week's schedule/projections don't need to survive a process restart. */
    private var cachedWeek: CachedWeek? = null

    suspend fun refreshDirectoryIfStale() {
        val lastSynced = preferences.getLong(LAST_SYNCED_KEY, 0)
        val isStale = System.currentTimeMillis() - lastSynced > DIRECTORY_MAX_AGE_MS
        if (!isStale && nflPlayerDao.count() > 0) return
        refreshDirectory()
    }

    private suspend fun refreshDirectory() {
        val players = sleeperApi.getAllPlayers().values
            .filter { it.active && it.position != null && it.position in FANTASY_POSITIONS }
            .map { dto ->
                NflPlayerEntity(
                    externalId = dto.player_id,
                    name = dto.full_name ?: dto.team.orEmpty().ifBlank { dto.player_id } + " D/ST",
                    position = Position.valueOf(dto.position!!),
                    nflTeam = dto.team ?: "FA",
                    injuryStatus = mapInjuryStatus(dto.injury_status),
                    injuryBodyPart = dto.injury_body_part,
                    injuryNotes = dto.injury_notes
                )
            }
        nflPlayerDao.replaceAll(players)
        preferences.edit { putLong(LAST_SYNCED_KEY, System.currentTimeMillis()) }
    }

    suspend fun searchPlayers(query: String): List<NflPlayer> {
        if (query.isBlank()) return emptyList()
        refreshDirectoryIfStale()
        return nflPlayerDao.search(query.trim()).map { it.toDomain() }
    }

    suspend fun getCachedPlayer(externalId: String): NflPlayer? =
        nflPlayerDao.getById(externalId)?.toDomain()

    suspend fun getWeeklyContext(externalId: String): WeeklyContext? {
        val week = currentWeek()
        val projection = week.projections[externalId]
        val cachedPlayer = nflPlayerDao.getById(externalId)
        return WeeklyContext(
            projectedPoints = projection.pointsFor(appSettingsRepository.scoringFormat.value),
            opponent = resolveOpponent(cachedPlayer?.nflTeam, week),
            injuryStatus = cachedPlayer?.injuryStatus ?: InjuryStatus.HEALTHY
        )
    }

    /** Refreshes projectedPoints/opponent/injuryStatus for every live-linked roster player. */
    suspend fun syncRoster(roster: List<Player>): List<Player> {
        refreshDirectoryIfStale()
        val week = currentWeek()
        val format = appSettingsRepository.scoringFormat.value
        return roster.map { player ->
            val externalId = player.externalId ?: return@map player
            val cachedPlayer = nflPlayerDao.getById(externalId) ?: return@map player
            val projection = week.projections[externalId]
            player.copy(
                nflTeam = cachedPlayer.nflTeam,
                opponent = resolveOpponent(cachedPlayer.nflTeam, week) ?: player.opponent,
                projectedPoints = if (projection != null) projection.pointsFor(format) else player.projectedPoints,
                injuryStatus = cachedPlayer.injuryStatus
            )
        }
    }

    /** null for an unresolvable team (e.g. a free agent); [BYE_WEEK_OPPONENT] when the team has no game this week. */
    private fun resolveOpponent(team: String?, week: CachedWeek): String? {
        if (team == null || team == "FA") return null
        return week.opponentByTeam[team] ?: BYE_WEEK_OPPONENT
    }

    suspend fun getPlayerDetailExtras(externalId: String): PlayerDetailExtras {
        val cached = nflPlayerDao.getById(externalId)
        val trend = runCatching { getTrend(externalId) }.getOrNull()
        return PlayerDetailExtras(
            trend = trend,
            injuryBodyPart = cached?.injuryBodyPart,
            injuryNotes = cached?.injuryNotes
        )
    }

    private suspend fun getTrend(externalId: String): TrendInfo? {
        sleeperApi.getTrendingAdds().find { it.player_id == externalId }?.let {
            return TrendInfo(TrendDirection.ADD, it.count)
        }
        sleeperApi.getTrendingDrops().find { it.player_id == externalId }?.let {
            return TrendInfo(TrendDirection.DROP, it.count)
        }
        return null
    }

    /** Trending-add players league-wide, excluding anyone already on this roster. */
    suspend fun getWaiverSuggestions(
        excludeExternalIds: Set<String>,
        position: Position? = null,
        limit: Int = 25
    ): List<WaiverSuggestion> {
        refreshDirectoryIfStale()
        val week = currentWeek()
        val format = appSettingsRepository.scoringFormat.value
        val trending = sleeperApi.getTrendingAdds(limit = 100)
        return trending
            .filter { it.player_id !in excludeExternalIds }
            .mapNotNull { trend ->
                val cached = nflPlayerDao.getById(trend.player_id) ?: return@mapNotNull null
                if (position != null && cached.position != position) return@mapNotNull null
                WaiverSuggestion(
                    player = cached.toDomain(),
                    trendCount = trend.count,
                    projectedPoints = week.projections[trend.player_id].pointsFor(format),
                    opponent = resolveOpponent(cached.nflTeam, week)
                )
            }
            .take(limit)
    }

    private fun SleeperProjectionDto?.pointsFor(format: ScoringFormat): Double {
        if (this == null) return 0.0
        return when (format) {
            ScoringFormat.PPR -> pts_ppr ?: pts_half_ppr ?: pts_std ?: 0.0
            ScoringFormat.HALF_PPR -> pts_half_ppr ?: pts_ppr ?: pts_std ?: 0.0
            ScoringFormat.STANDARD -> pts_std ?: pts_half_ppr ?: pts_ppr ?: 0.0
        }
    }

    private suspend fun currentWeek(): CachedWeek {
        cachedWeek?.let { cached ->
            if (System.currentTimeMillis() - cached.fetchedAtMillis < TimeUnit.MINUTES.toMillis(15)) {
                return cached
            }
        }
        val state = sleeperApi.getState()
        val projections = sleeperApi.getProjections(state.season, state.week)
        val scoreboard = espnApi.getScoreboard(week = state.week, year = state.season)
        val opponentByTeam = mutableMapOf<String, String>()
        scoreboard.events.forEach { event ->
            val competitors = event.competitions.firstOrNull()?.competitors.orEmpty()
            val home = competitors.firstOrNull { it.homeAway == "home" }
            val away = competitors.firstOrNull { it.homeAway == "away" }
            if (home != null && away != null) {
                opponentByTeam[home.team.abbreviation] = away.team.abbreviation
                opponentByTeam[away.team.abbreviation] = home.team.abbreviation
            }
        }
        return CachedWeek(projections, opponentByTeam, System.currentTimeMillis()).also { cachedWeek = it }
    }

    private fun mapInjuryStatus(raw: String?): InjuryStatus = when (raw?.trim()?.lowercase()) {
        null, "", "active" -> InjuryStatus.HEALTHY
        "questionable" -> InjuryStatus.QUESTIONABLE
        "doubtful" -> InjuryStatus.DOUBTFUL
        else -> InjuryStatus.OUT // "Out", "IR", "PUP", "Suspended", etc. — don't start them
    }

    private data class CachedWeek(
        val projections: Map<String, SleeperProjectionDto>,
        val opponentByTeam: Map<String, String>,
        val fetchedAtMillis: Long
    )
}
