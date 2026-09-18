package com.sitorplay.app.domain.model

enum class Call { START, SIT }

data class Recommendation(
    val player: Player,
    val call: Call,
    val adjustedProjection: Double,
    val reasons: List<String>
)
