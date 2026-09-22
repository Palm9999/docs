package com.sitorplay.app.domain.model

import com.sitorplay.app.domain.prediction.Projection

enum class Call { START, SIT }

data class Recommendation(
    val player: Player,
    val call: Call,
    val adjustedProjection: Double,
    val reasons: List<String>,
    /**
     * The model's floor/median/ceiling for this player, when it had something to
     * say. Null means the ranking came from the projection heuristic instead --
     * an unmodelled position, or a player missing from this week's bundle.
     */
    val projection: Projection? = null
)
