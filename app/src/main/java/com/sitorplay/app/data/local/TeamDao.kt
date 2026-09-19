package com.sitorplay.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamDao {
    @Query("SELECT * FROM teams ORDER BY id")
    fun observeAll(): Flow<List<TeamEntity>>

    @Query("SELECT COUNT(*) FROM teams")
    suspend fun count(): Int

    @Query("SELECT * FROM teams WHERE id = :id")
    suspend fun getById(id: Long): TeamEntity?

    @Insert
    suspend fun insert(team: TeamEntity): Long

    @Update
    suspend fun update(team: TeamEntity)

    @Delete
    suspend fun delete(team: TeamEntity)
}
