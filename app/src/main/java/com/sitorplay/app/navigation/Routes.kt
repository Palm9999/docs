package com.sitorplay.app.navigation

object Routes {
    const val ROSTER = "roster"
    const val WAIVER_WIRE = "waiver_wire"
    const val COMPARE = "compare"
    const val SETTINGS = "settings"
    const val ADD_PLAYER = "add_player?prefillExternalId={prefillExternalId}"
    const val PLAYER_DETAIL = "player_detail/{playerId}"

    fun playerDetail(playerId: Long) = "player_detail/$playerId"

    fun addPlayer(prefillExternalId: String? = null): String =
        if (prefillExternalId != null) "add_player?prefillExternalId=$prefillExternalId" else "add_player"
}
