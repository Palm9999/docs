package com.sitorplay.app.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.sitorplay.app.data.local.AppDatabase
import com.sitorplay.app.data.local.NflPlayerDao
import com.sitorplay.app.data.local.PlayerDao
import com.sitorplay.app.data.local.TeamDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            // The cached NFL player directory is just a re-fetchable cache, so a
            // destructive migration is safe and avoids hand-writing schema migrations
            // for a table that gets fully replaced on every sync anyway.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun providePlayerDao(database: AppDatabase): PlayerDao = database.playerDao()

    @Provides
    fun provideNflPlayerDao(database: AppDatabase): NflPlayerDao = database.nflPlayerDao()

    @Provides
    fun provideTeamDao(database: AppDatabase): TeamDao = database.teamDao()

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("sitorplay_prefs", Context.MODE_PRIVATE)
}
