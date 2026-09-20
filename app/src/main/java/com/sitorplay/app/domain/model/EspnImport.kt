package com.sitorplay.app.domain.model

/**
 * Credentials needed to read a *private* ESPN Fantasy league (public leagues need none of this).
 *
 * [cookieHeader] is the *entire* Cookie header value from a real logged-in request, not just
 * espn_s2/SWID — ESPN's site sits behind bot-mitigation that also checks other session cookies
 * a plain HTTP client wouldn't normally send, so a raw two-cookie header alone gets bounced.
 */
data class EspnCredentials(
    val leagueId: String,
    val season: String,
    val cookieHeader: String
)

/** One team inside an ESPN league, shown so the user can pick which one is theirs. */
data class EspnLeagueTeam(
    val espnTeamId: Int,
    val name: String
)
