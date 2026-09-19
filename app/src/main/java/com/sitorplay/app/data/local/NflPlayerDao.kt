package com.sitorplay.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface NflPlayerDao {

    @Query("SELECT COUNT(*) FROM nfl_players")
    suspend fun count(): Int

    @Query(
        """
        SELECT * FROM nfl_players
        WHERE name LIKE '%' || :query || '%'
        ORDER BY name
        LIMIT 30
        """
    )
    suspend fun search(query: String): List<NflPlayerEntity>

    @Query("SELECT * FROM nfl_players WHERE externalId = :externalId")
    suspend fun getById(externalId: String): NflPlayerEntity?

    @Query("SELECT * FROM nfl_players WHERE position = 'DEF' AND nflTeam = :team LIMIT 1")
    suspend fun findDefenseByTeam(team: String): NflPlayerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(players: List<NflPlayerEntity>)

    @Query("DELETE FROM nfl_players")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(players: List<NflPlayerEntity>) {
        clear()
        insertAll(players)
    }
}
