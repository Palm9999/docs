package com.sitorplay.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players WHERE teamId = :teamId ORDER BY position, projectedPoints DESC")
    fun observeByTeam(teamId: Long): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE id = :id")
    fun observeById(id: Long): Flow<PlayerEntity?>

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun getById(id: Long): PlayerEntity?

    @Query("SELECT COUNT(*) FROM players WHERE teamId = :teamId")
    suspend fun countForTeam(teamId: Long): Int

    @Query("SELECT externalId FROM players WHERE teamId = :teamId AND externalId IS NOT NULL")
    suspend fun rosteredExternalIds(teamId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(player: PlayerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(players: List<PlayerEntity>)

    @Update
    suspend fun update(player: PlayerEntity)

    @Delete
    suspend fun delete(player: PlayerEntity)
}
