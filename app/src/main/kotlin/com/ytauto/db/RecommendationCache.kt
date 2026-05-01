package com.ytauto.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recommendation_cache")
data class RecommendationCache(
    @PrimaryKey val videoUrl: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val genre: String, // De mix waartoe dit behoort (bijv. "Oujda", "Franse Rap")
    val timestamp: Long = System.currentTimeMillis()
)
