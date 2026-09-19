package com.sitorplay.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * ESPN's unofficial v3 Fantasy Football API. A *private* league requires the `espn_s2` and
 * `SWID` cookie values from a logged-in browser session — there's no public-only path for
 * private leagues, unlike the site scoreboard API used elsewhere in [com.sitorplay.app.data.remote.EspnApi].
 */
@Serializable
data class EspnLeagueDto(
    val id: Long = 0,
    val teams: List<EspnFantasyTeamDto> = emptyList()
)

@Serializable
data class EspnFantasyTeamDto(
    val id: Int,
    val location: String? = null,
    val nickname: String? = null,
    val name: String? = null,
    val roster: EspnRosterDto? = null
) {
    fun displayName(): String {
        val explicit = name?.trim().orEmpty()
        if (explicit.isNotBlank()) return explicit
        val combined = listOfNotNull(location, nickname).joinToString(" ").trim()
        return combined.ifBlank { "Team $id" }
    }
}

@Serializable
data class EspnRosterDto(
    val entries: List<EspnRosterEntryDto> = emptyList()
)

@Serializable
data class EspnRosterEntryDto(
    val lineupSlotId: Int,
    val playerPoolEntry: EspnPlayerPoolEntryDto
)

@Serializable
data class EspnPlayerPoolEntryDto(
    val player: EspnFantasyPlayerDto
)

@Serializable
data class EspnFantasyPlayerDto(
    val fullName: String,
    val defaultPositionId: Int,
    val proTeamId: Int = 0,
    val injuryStatus: String? = null
)
