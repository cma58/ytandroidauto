package com.ytauto.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineTrackDao {
    @Query("SELECT * FROM offline_tracks ORDER BY downloadedAt DESC")
    fun getAllTracks(): Flow<List<OfflineTrack>>

    @Query("SELECT * FROM offline_tracks ORDER BY downloadedAt DESC")
    suspend fun getAllTracksOnce(): List<OfflineTrack>

    @Query("SELECT * FROM offline_tracks WHERE videoUrl = :videoUrl LIMIT 1")
    suspend fun getTrackByUrl(videoUrl: String): OfflineTrack?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: OfflineTrack)

    @Delete
    suspend fun deleteTrack(track: OfflineTrack)

    @Query("SELECT * FROM offline_tracks ORDER BY RANDOM() LIMIT 20")
    suspend fun getRandomTracks(): List<OfflineTrack>

    @Query("UPDATE offline_tracks SET isFavorite = :favorite WHERE videoUrl = :videoUrl")
    suspend fun setFavorite(videoUrl: String, favorite: Boolean)

    @Query("UPDATE offline_tracks SET playCount = playCount + 1 WHERE videoUrl = :videoUrl")
    suspend fun incrementPlayCount(videoUrl: String)

    @Query("SELECT * FROM offline_tracks WHERE playCount > 0 ORDER BY playCount DESC LIMIT 10")
    suspend fun getTopPlayedTracks(): List<OfflineTrack>
}
