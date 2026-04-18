package com.ytauto.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * OfflineTrack - Database entiteit voor gedownloade nummers.
 */
@Entity(tableName = "offline_tracks")
data class OfflineTrack(
    @PrimaryKey val videoUrl: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val localPath: String,
    val durationSeconds: Long,
    val downloadedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val playCount: Int = 0
)
