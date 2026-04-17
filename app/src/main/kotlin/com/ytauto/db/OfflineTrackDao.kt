package com.ytauto.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineTrackDao {
    @Query("SELECT * FROM offline_tracks ORDER BY downloadedAt DESC")
    fun getAllTracks(): Flow<List<OfflineTrack>>

    @Query("SELECT * FROM offline_tracks WHERE videoUrl = :videoUrl LIMIT 1")
    suspend fun getTrackByUrl(videoUrl: String): OfflineTrack?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: OfflineTrack)

    @Delete
    suspend fun deleteTrack(track: OfflineTrack)
}
