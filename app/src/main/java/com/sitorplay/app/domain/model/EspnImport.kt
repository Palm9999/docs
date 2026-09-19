package com.sitorplay.app.domain.model

/** Credentials needed to read a *private* ESPN Fantasy league (public leagues need none of this). */
data class EspnCredentials(
    val leagueId: String,
    val season: String,
    val espnS2: String,
    val swid: String
)

/** One team inside an ESPN league, shown so the user can pick which one is theirs. */
data class EspnLeagueTeam(
    val espnTeamId: Int,
    val name: String
)
