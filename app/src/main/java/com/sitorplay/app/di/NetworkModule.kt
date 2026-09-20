package com.sitorplay.app.di

import com.sitorplay.app.data.remote.EspnApi
import com.sitorplay.app.data.remote.EspnFantasyApi
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
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

private const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Mobile Safari/537.36"

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SleeperRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EspnRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EspnFantasyRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val SLEEPER_BASE_URL = "https://api.sleeper.app/"
    private const val ESPN_BASE_URL = "https://site.api.espn.com/"
    private const val ESPN_FANTASY_BASE_URL = "https://fantasy.espn.com/"

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

    /**
     * ESPN's fantasy API is picky in ways a plain OkHttp client isn't by default: it wants a
     * browser-like User-Agent/Accept, and — when the session cookies don't authenticate — it
     * serves the fantasy site's HTML app shell (200 OK) instead of an error status, which would
     * otherwise surface as an opaque JSON-parsing crash. Fail fast with a clear message instead.
     */
    @Provides
    @Singleton
    @EspnFantasyRetrofit
    fun provideEspnFantasyOkHttpClient(client: OkHttpClient): OkHttpClient = client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "application/json")
                .build()
            val response = chain.proceed(request)
            val contentType = response.header("Content-Type").orEmpty()
            if (!contentType.contains("json", ignoreCase = true)) {
                val requestedUrl = response.request.url
                val status = response.code
                response.close()
                throw IOException(
                    "ESPN returned a non-JSON response (HTTP $status, content-type " +
                        "\"$contentType\") from $requestedUrl instead of league data. This " +
                        "usually means the login session wasn't accepted."
                )
            }
            response
        }
        .build()

    @Provides
    @Singleton
    @EspnFantasyRetrofit
    fun provideEspnFantasyRetrofit(
        @EspnFantasyRetrofit client: OkHttpClient,
        json: Json
    ): Retrofit = Retrofit.Builder()
        .baseUrl(ESPN_FANTASY_BASE_URL)
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

    @Provides
    @Singleton
    fun provideEspnFantasyApi(@EspnFantasyRetrofit retrofit: Retrofit): EspnFantasyApi =
        retrofit.create(EspnFantasyApi::class.java)
}
