package com.sitorplay.app.di

import com.sitorplay.app.data.repository.PlayerRepository
import com.sitorplay.app.data.repository.PlayerRepositoryImpl
import com.sitorplay.app.data.repository.TeamRepository
import com.sitorplay.app.data.repository.TeamRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPlayerRepository(impl: PlayerRepositoryImpl): PlayerRepository

    @Binds
    @Singleton
    abstract fun bindTeamRepository(impl: TeamRepositoryImpl): TeamRepository
}
