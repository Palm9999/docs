package com.sitorplay.app.data.remote

import com.sitorplay.app.data.remote.dto.EspnLeagueDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/** ESPN's unofficial v3 Fantasy Football API, used to import a private league's roster. */
interface EspnFantasyApi {

    @GET("apis/v3/games/ffl/seasons/{season}/segments/0/leagues/{leagueId}")
    suspend fun getLeague(
        @Path("season") season: Int,
        @Path("leagueId") leagueId: Long,
        @Header("Cookie") cookie: String,
        @Query("view") views: List<String> = listOf("mRoster", "mTeam")
    ): EspnLeagueDto
}
