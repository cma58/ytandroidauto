package com.ytauto.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecommendationCacheDao {
    @Query("SELECT * FROM recommendation_cache WHERE genre = :genre ORDER BY timestamp DESC")
    fun getRecommendationsByGenre(genre: String): Flow<List<RecommendationCache>>

    @Query("SELECT * FROM recommendation_cache ORDER BY timestamp DESC")
    fun getAllRecommendations(): Flow<List<RecommendationCache>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recommendations: List<RecommendationCache>): List<Long>

    @Query("DELETE FROM recommendation_cache WHERE genre = :genre")
    suspend fun deleteByGenre(genre: String): Int

    @Query("SELECT COUNT(*) FROM recommendation_cache")
    suspend fun getCount(): Int
}
