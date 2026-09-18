package com.sitorplay.app.di

import android.content.Context
import androidx.room.Room
import com.sitorplay.app.data.local.AppDatabase
import com.sitorplay.app.data.local.PlayerDao
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
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME).build()

    @Provides
    fun providePlayerDao(database: AppDatabase): PlayerDao = database.playerDao()
}
