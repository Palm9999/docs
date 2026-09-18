package com.sitorplay.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sitorplay.app.domain.model.InjuryStatus
import com.sitorplay.app.domain.model.Player
import com.sitorplay.app.domain.model.Position
import com.sitorplay.app.domain.model.RosterSlot

@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Position,
    val nflTeam: String,
    val opponent: String,
    val projectedPoints: Double,
    val opponentDefenseRank: Int,
    val injuryStatus: InjuryStatus,
    val rosterSlot: RosterSlot
)

fun PlayerEntity.toDomain(): Player = Player(
    id = id,
    name = name,
    position = position,
    nflTeam = nflTeam,
    opponent = opponent,
    projectedPoints = projectedPoints,
    opponentDefenseRank = opponentDefenseRank,
    injuryStatus = injuryStatus,
    rosterSlot = rosterSlot
)

fun Player.toEntity(): PlayerEntity = PlayerEntity(
    id = id,
    name = name,
    position = position,
    nflTeam = nflTeam,
    opponent = opponent,
    projectedPoints = projectedPoints,
    opponentDefenseRank = opponentDefenseRank,
    injuryStatus = injuryStatus,
    rosterSlot = rosterSlot
)
