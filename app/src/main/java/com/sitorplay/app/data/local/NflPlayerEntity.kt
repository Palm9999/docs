package com.sitorplay.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sitorplay.app.domain.model.InjuryStatus
import com.sitorplay.app.domain.model.NflPlayer
import com.sitorplay.app.domain.model.Position
import com.sitorplay.app.domain.model.PracticeParticipation

/** A locally cached snapshot of one entry from Sleeper's public player directory. */
@Entity(tableName = "nfl_players")
data class NflPlayerEntity(
    @PrimaryKey val externalId: String,
    val name: String,
    val position: Position,
    val nflTeam: String,
    val injuryStatus: InjuryStatus,
    val practiceParticipation: PracticeParticipation = PracticeParticipation.UNKNOWN,
    val injuryBodyPart: String? = null,
    val injuryNotes: String? = null
)

fun NflPlayerEntity.toDomain(): NflPlayer = NflPlayer(
    externalId = externalId,
    name = name,
    position = position,
    nflTeam = nflTeam,
    injuryStatus = injuryStatus,
    practiceParticipation = practiceParticipation,
    injuryBodyPart = injuryBodyPart,
    injuryNotes = injuryNotes
)
