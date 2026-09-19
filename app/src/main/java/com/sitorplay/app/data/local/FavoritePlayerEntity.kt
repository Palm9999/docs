package com.sitorplay.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_players")
data class FavoritePlayerEntity(
    @PrimaryKey val externalId: String,
    val addedAt: Long = System.currentTimeMillis()
)
