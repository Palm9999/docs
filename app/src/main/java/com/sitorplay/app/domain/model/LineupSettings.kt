package com.sitorplay.app.domain.model

/**
 * How many of each starter slot this league uses. FLEX is always filled from the
 * best remaining RB/WR/TE. Setting a slot to 0 disables it (e.g. no-kicker leagues).
 */
data class LineupSettings(
    val qb: Int = 1,
    val rb: Int = 2,
    val wr: Int = 2,
    val te: Int = 1,
    val flex: Int = 1,
    val k: Int = 1,
    val def: Int = 1
) {
    fun starterSlots(): Map<Position, Int> = buildMap {
        if (qb > 0) put(Position.QB, qb)
        if (rb > 0) put(Position.RB, rb)
        if (wr > 0) put(Position.WR, wr)
        if (te > 0) put(Position.TE, te)
        if (k > 0) put(Position.K, k)
        if (def > 0) put(Position.DEF, def)
    }
}
