package com.ytauto.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ytauto.data.AppDownloader
import com.ytauto.data.YouTubeRepository
import com.ytauto.db.AppDatabase

/**
 * DownloadWorker — Achtergrondtaak voor het synchroniseren van offline tracks.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val downloader = AppDownloader.getInstance(context)
    private val youtubeRepo = YouTubeRepository()

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting Smart Sync...")
        
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val offlineDao = db.offlineTrackDao()
            
            // Haal een paar willekeurige opgeslagen nummers op om suggesties op te baseren
            val existing = offlineDao.getRandomTracks()
            val seedArtist = existing.randomOrNull()?.artist ?: "Lofi hip hop"
            
            Log.d(TAG, "Seeding sync with artist: $seedArtist")
            val results = youtubeRepo.search(seedArtist, maxResults = 5)
            
            results.forEach { result ->
                val alreadyDownloaded = offlineDao.getTrackByUrl(result.videoUrl) != null
                if (!alreadyDownloaded) {
                    Log.d(TAG, "Syncing new track: ${result.title}")
                    downloader.downloadTrack(result.videoUrl)
                }
            }
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            return Result.retry()
        }
    }

    companion object {
        private const val TAG = "DownloadWorker"
    }
}
