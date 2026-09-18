package com.sitorplay.app.data.remote

import com.sitorplay.app.data.remote.dto.SleeperPlayerDto
import com.sitorplay.app.data.remote.dto.SleeperProjectionDto
import com.sitorplay.app.data.remote.dto.SleeperStateDto
import retrofit2.http.GET
import retrofit2.http.Path

/** https://docs.sleeper.com — free, public, no API key required. */
interface SleeperApi {

    @GET("v1/state/nfl")
    suspend fun getState(): SleeperStateDto

    /** ~14k entries. Sleeper asks that this only be called at most once a day. */
    @GET("v1/players/nfl")
    suspend fun getAllPlayers(): Map<String, SleeperPlayerDto>

    @GET("v1/projections/nfl/regular/{season}/{week}")
    suspend fun getProjections(
        @Path("season") season: String,
        @Path("week") week: Int
    ): Map<String, SleeperProjectionDto>
}
