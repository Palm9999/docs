package com.sitorplay.app.di

import com.sitorplay.app.data.remote.EspnApi
import com.sitorplay.app.data.remote.SleeperApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SleeperRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EspnRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val SLEEPER_BASE_URL = "https://api.sleeper.app/"
    private const val ESPN_BASE_URL = "https://site.api.espn.com/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @SleeperRetrofit
    fun provideSleeperRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(SLEEPER_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    @EspnRetrofit
    fun provideEspnRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(ESPN_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideSleeperApi(@SleeperRetrofit retrofit: Retrofit): SleeperApi =
        retrofit.create(SleeperApi::class.java)

    @Provides
    @Singleton
    fun provideEspnApi(@EspnRetrofit retrofit: Retrofit): EspnApi =
        retrofit.create(EspnApi::class.java)
}
