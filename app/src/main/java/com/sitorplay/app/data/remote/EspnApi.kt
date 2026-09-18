package com.sitorplay.app.data.remote

import com.sitorplay.app.data.remote.dto.EspnScoreboardDto
import retrofit2.http.GET
import retrofit2.http.Query

/** ESPN's public (unofficial, undocumented) site API — free, no API key required. */
interface EspnApi {

    @GET("apis/site/v2/sports/football/nfl/scoreboard")
    suspend fun getScoreboard(
        @Query("week") week: Int,
        @Query("year") year: String,
        @Query("seasontype") seasonType: Int = 2
    ): EspnScoreboardDto
}
