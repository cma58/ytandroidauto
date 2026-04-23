package com.ytauto.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentTrackDao {
    @Query("SELECT * FROM recent_tracks ORDER BY lastPlayedAt DESC LIMIT 50")
    fun getAllRecentTracks(): Flow<List<RecentTrack>>

    @Query("SELECT * FROM recent_tracks ORDER BY lastPlayedAt DESC LIMIT 50")
    suspend fun getAllRecentTracksOnce(): List<RecentTrack>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentTrack(track: RecentTrack): Long

    @Query("DELETE FROM recent_tracks WHERE lastPlayedAt < (SELECT MIN(lastPlayedAt) FROM (SELECT lastPlayedAt FROM recent_tracks ORDER BY lastPlayedAt DESC LIMIT 50))")
    suspend fun trimRecentTracks(): Int

    @Transaction
    suspend fun addAndTrim(track: RecentTrack) {
        insertRecentTrack(track)
        trimRecentTracks()
    }
}
