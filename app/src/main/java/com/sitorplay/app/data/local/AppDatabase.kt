package com.sitorplay.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [PlayerEntity::class, NflPlayerEntity::class, TeamEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun nflPlayerDao(): NflPlayerDao
    abstract fun teamDao(): TeamDao

    companion object {
        const val DATABASE_NAME = "sitorplay.db"
    }
}
