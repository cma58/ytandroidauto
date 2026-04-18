package com.ytauto.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * RecentTrack - Database entiteit voor recent afgespeelde nummers (zowel online als offline).
 */
@Entity(tableName = "recent_tracks")
data class RecentTrack(
    @PrimaryKey val videoUrl: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val lastPlayedAt: Long = System.currentTimeMillis()
)
