package com.sitorplay.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sitorplay.app.domain.model.Team

@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

fun TeamEntity.toDomain(): Team = Team(id = id, name = name)

fun Team.toEntity(): TeamEntity = TeamEntity(id = id, name = name)
