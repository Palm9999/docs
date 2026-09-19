package com.sitorplay.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SleeperStateDto(
    val week: Int,
    val season: String,
    val season_type: String
)

/**
 * Only the fields we actually use from Sleeper's ~14k-entry player dictionary.
 * https://api.sleeper.app/v1/players/nfl (fetch at most once a day, per Sleeper's guidance).
 */
@Serializable
data class SleeperPlayerDto(
    val player_id: String,
    val full_name: String? = null,
    val first_name: String? = null,
    val last_name: String? = null,
    val position: String? = null,
    val team: String? = null,
    val active: Boolean = false,
    val injury_status: String? = null,
    val injury_body_part: String? = null,
    val injury_notes: String? = null
)

@Serializable
data class SleeperProjectionDto(
    val pts_ppr: Double? = null,
    val pts_half_ppr: Double? = null,
    val pts_std: Double? = null
)

/** One row of https://api.sleeper.app/v1/players/nfl/trending/{add|drop}. */
@Serializable
data class TrendingPlayerDto(
    val player_id: String,
    val count: Int
)
