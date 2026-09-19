package com.sitorplay.app.data.repository

import com.sitorplay.app.data.local.NflPlayerDao
import com.sitorplay.app.data.local.toDomain
import com.sitorplay.app.data.remote.EspnFantasyApi
import com.sitorplay.app.data.remote.dto.EspnFantasyPlayerDto
import com.sitorplay.app.data.remote.dto.EspnRosterEntryDto
import com.sitorplay.app.data.sync.NflDataRepository
import com.sitorplay.app.domain.model.EspnCredentials
import com.sitorplay.app.domain.model.EspnLeagueTeam
import com.sitorplay.app.domain.model.InjuryStatus
import com.sitorplay.app.domain.model.NflPlayer
import com.sitorplay.app.domain.model.Player
import com.sitorplay.app.domain.model.Position
import com.sitorplay.app.domain.model.RosterSlot
import javax.inject.Inject
import javax.inject.Singleton

private const val BENCH_SLOT = 20
private const val IR_SLOT = 21

/** ESPN `player.defaultPositionId` -> our [Position]. Kicker and D/ST use ESPN's fixed ids. */
private val POSITION_BY_ESPN_ID = mapOf(
    1 to Position.QB,
    2 to Position.RB,
    3 to Position.WR,
    4 to Position.TE,
    5 to Position.K,
    16 to Position.DEF
)

/** ESPN `proTeamId` -> team abbreviation. Stable across seasons. */
private val PRO_TEAM_ABBREVIATIONS = mapOf(
    1 to "ATL", 2 to "BUF", 3 to "CHI", 4 to "CIN", 5 to "CLE", 6 to "DAL", 7 to "DEN",
    8 to "DET", 9 to "GB", 10 to "TEN", 11 to "IND", 12 to "KC", 13 to "LV", 14 to "LAR",
    15 to "MIA", 16 to "MIN", 17 to "NE", 18 to "NO", 19 to "NYG", 20 to "NYJ", 21 to "PHI",
    22 to "ARI", 23 to "PIT", 24 to "LAC", 25 to "SF", 26 to "SEA", 27 to "TB", 28 to "WSH",
    29 to "CAR", 30 to "JAX", 33 to "BAL", 34 to "HOU"
)

/**
 * Imports a roster from a *private* ESPN Fantasy league (requires the espn_s2/SWID cookies
 * from a logged-in browser session — ESPN has no public API for private leagues).
 *
 * Players are matched against the existing Sleeper-backed [NflPlayerDao] cache by name so the
 * imported roster keeps live-syncing projections/opponents/injury status afterward, the same
 * as a player added via search. A player ESPN reports that we can't confidently match is still
 * imported, just without an [Player.externalId] (so it won't live-sync until re-added manually).
 */
@Singleton
class EspnImportRepository @Inject constructor(
    private val espnFantasyApi: EspnFantasyApi,
    private val nflPlayerDao: NflPlayerDao,
    private val nflDataRepository: NflDataRepository,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository
) {

    suspend fun fetchLeagueTeams(credentials: EspnCredentials): List<EspnLeagueTeam> {
        val league = fetchLeague(credentials)
        return league.teams
            .map { EspnLeagueTeam(espnTeamId = it.id, name = it.displayName()) }
            .sortedBy { it.name }
    }

    /** Creates a new local team named [desiredName] and imports the ESPN team's roster into it. */
    suspend fun importTeamAsNewTeam(
        credentials: EspnCredentials,
        espnTeamId: Int,
        desiredName: String
    ): Long {
        nflDataRepository.refreshDirectoryIfStale()
        val league = fetchLeague(credentials)
        val espnTeam = league.teams.find { it.id == espnTeamId }
            ?: error("That team is no longer in this league.")
        val localTeamId = teamRepository.addTeam(desiredName)
        espnTeam.roster?.entries.orEmpty().forEach { entry ->
            playerRepository.addPlayer(mapEntry(entry).copy(teamId = localTeamId))
        }
        return localTeamId
    }

    private suspend fun fetchLeague(credentials: EspnCredentials) = espnFantasyApi.getLeague(
        season = credentials.season.trim().toInt(),
        leagueId = credentials.leagueId.trim().toLong(),
        cookie = "espn_s2=${credentials.espnS2.trim()}; SWID=${credentials.swid.trim()}"
    )

    private suspend fun mapEntry(entry: EspnRosterEntryDto): Player {
        val dto = entry.playerPoolEntry.player
        val position = POSITION_BY_ESPN_ID[dto.defaultPositionId] ?: Position.WR
        val nflTeam = PRO_TEAM_ABBREVIATIONS[dto.proTeamId] ?: "FA"
        val matched = matchSleeperPlayer(dto, position, nflTeam)
        val rosterSlot = if (entry.lineupSlotId == BENCH_SLOT || entry.lineupSlotId == IR_SLOT) {
            RosterSlot.BENCH
        } else {
            RosterSlot.STARTER
        }
        return Player(
            name = matched?.name ?: dto.fullName,
            position = position,
            nflTeam = matched?.nflTeam ?: nflTeam,
            opponent = "",
            projectedPoints = 0.0,
            opponentDefenseRank = 16,
            injuryStatus = matched?.injuryStatus ?: mapEspnInjuryStatus(dto.injuryStatus),
            rosterSlot = rosterSlot,
            externalId = matched?.externalId
        )
    }

    private suspend fun matchSleeperPlayer(
        dto: EspnFantasyPlayerDto,
        position: Position,
        nflTeam: String
    ): NflPlayer? {
        if (position == Position.DEF) {
            return nflPlayerDao.findDefenseByTeam(nflTeam)?.toDomain()
        }
        val target = normalizeName(dto.fullName)
        val lastToken = dto.fullName.trim().substringAfterLast(' ')
        val candidates = nflPlayerDao.search(lastToken).filter { it.position == position }
        return candidates.firstOrNull { normalizeName(it.name) == target }?.toDomain()
            ?: candidates.firstOrNull()?.toDomain()
    }

    private fun normalizeName(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z ]"), "")
            .replace(Regex("\\b(jr|sr|ii|iii|iv|v)\\b"), "")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun mapEspnInjuryStatus(raw: String?): InjuryStatus = when (raw?.trim()?.uppercase()) {
        null, "", "ACTIVE" -> InjuryStatus.HEALTHY
        "QUESTIONABLE" -> InjuryStatus.QUESTIONABLE
        "DOUBTFUL" -> InjuryStatus.DOUBTFUL
        else -> InjuryStatus.OUT // OUT, INJURY_RESERVE, SUSPENSION, etc.
    }
}
