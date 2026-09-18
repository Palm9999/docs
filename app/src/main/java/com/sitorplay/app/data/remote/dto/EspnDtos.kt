package com.sitorplay.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class EspnScoreboardDto(
    val events: List<EspnEventDto> = emptyList()
)

@Serializable
data class EspnEventDto(
    val competitions: List<EspnCompetitionDto> = emptyList()
)

@Serializable
data class EspnCompetitionDto(
    val competitors: List<EspnCompetitorDto> = emptyList()
)

@Serializable
data class EspnCompetitorDto(
    val team: EspnTeamDto,
    val homeAway: String
)

@Serializable
data class EspnTeamDto(
    val abbreviation: String
)
