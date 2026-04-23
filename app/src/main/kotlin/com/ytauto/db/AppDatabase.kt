package com.ytauto.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [OfflineTrack::class, RecentTrack::class, PlayEvent::class, RecommendationCache::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun offlineTrackDao(): OfflineTrackDao
    abstract fun recentTrackDao(): RecentTrackDao
    abstract fun playEventDao(): PlayEventDao
    abstract fun recommendationCacheDao(): RecommendationCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ytauto_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
