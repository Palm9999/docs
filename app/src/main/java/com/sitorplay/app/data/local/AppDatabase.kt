package com.sitorplay.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [PlayerEntity::class, NflPlayerEntity::class, TeamEntity::class, FavoritePlayerEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun nflPlayerDao(): NflPlayerDao
    abstract fun teamDao(): TeamDao
    abstract fun favoritePlayerDao(): FavoritePlayerDao

    companion object {
        const val DATABASE_NAME = "sitorplay.db"
    }
}
