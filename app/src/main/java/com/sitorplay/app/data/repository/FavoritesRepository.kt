package com.sitorplay.app.data.repository

import com.sitorplay.app.data.local.FavoritePlayerDao
import com.sitorplay.app.data.local.FavoritePlayerEntity
import com.sitorplay.app.data.local.NflPlayerDao
import com.sitorplay.app.data.local.toDomain
import com.sitorplay.app.domain.model.NflPlayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepository @Inject constructor(
    private val favoritePlayerDao: FavoritePlayerDao,
    private val nflPlayerDao: NflPlayerDao
) {
    fun observeFavoriteIds(): Flow<Set<String>> =
        favoritePlayerDao.observeFavoriteIds().map { it.toSet() }

    suspend fun toggleFavorite(externalId: String) {
        if (favoritePlayerDao.isFavorite(externalId)) {
            favoritePlayerDao.remove(externalId)
        } else {
            favoritePlayerDao.add(FavoritePlayerEntity(externalId))
        }
    }

    suspend fun getFavoritePlayers(): List<NflPlayer> {
        val ids = favoritePlayerDao.observeFavoriteIds().first()
        return ids.mapNotNull { id -> nflPlayerDao.getById(id)?.toDomain() }
    }
}
