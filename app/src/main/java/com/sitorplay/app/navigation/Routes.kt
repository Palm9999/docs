package com.sitorplay.app.navigation

object Routes {
    const val ROSTER = "roster"
    const val ADD_PLAYER = "add_player"
    const val PLAYER_DETAIL = "player_detail/{playerId}"

    fun playerDetail(playerId: Long) = "player_detail/$playerId"
}
