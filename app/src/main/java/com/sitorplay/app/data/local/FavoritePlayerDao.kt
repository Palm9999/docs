package com.sitorplay.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritePlayerDao {
    @Query("SELECT externalId FROM favorite_players ORDER BY addedAt DESC")
    fun observeFavoriteIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_players WHERE externalId = :externalId)")
    suspend fun isFavorite(externalId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: FavoritePlayerEntity)

    @Query("DELETE FROM favorite_players WHERE externalId = :externalId")
    suspend fun remove(externalId: String)
}
