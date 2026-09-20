package com.sitorplay.app.domain.model

/**
 * Which league/season to import. Login itself isn't tracked here — the user authenticates via
 * an embedded WebView (see [com.sitorplay.app.ui.settings.EspnLoginDialog]), and the resulting
 * session lives in Android's own [android.webkit.CookieManager], not in app state.
 */
data class EspnCredentials(
    val leagueId: String,
    val season: String
)

/** One team inside an ESPN league, shown so the user can pick which one is theirs. */
data class EspnLeagueTeam(
    val espnTeamId: Int,
    val name: String
)
