package com.ytauto.db

import androidx.room.*

@Entity(tableName = "play_events")
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: String,
    val title: String,
    val artist: String?,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface PlayEventDao {
    @Insert
    suspend fun insertEvent(event: PlayEvent)

    @Query("SELECT artist, COUNT(*) as count FROM play_events WHERE artist IS NOT NULL GROUP BY artist ORDER BY count DESC LIMIT 5")
    suspend fun getTopArtists(): List<ArtistCount>

    @Query("SELECT title FROM play_events GROUP BY title ORDER BY COUNT(*) DESC LIMIT 10")
    suspend fun getTopTitles(): List<String>

    @Query("DELETE FROM play_events")
    suspend fun clearAnalytics()
}

data class ArtistCount(val artist: String, val count: Int)
