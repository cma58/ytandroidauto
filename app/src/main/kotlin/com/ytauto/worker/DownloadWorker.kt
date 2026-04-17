package com.ytauto.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ytauto.data.YouTubeRepository
import com.ytauto.db.AppDatabase
import com.ytauto.db.OfflineTrack
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * DownloadWorker - Verwerkt het downloaden van YouTube tracks op de achtergrond.
 */
class DownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val youtubeRepo = YouTubeRepository()
    private val okHttpClient = OkHttpClient()

    override suspend fun doWork(): Result {
        val videoUrl = inputData.getString(KEY_VIDEO_URL) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "unknown_track"
        
        Log.d(TAG, "Starting download for: $title ($videoUrl)")

        return try {
            // 1. Verkrijg de stream URL
            val streamUrl = youtubeRepo.getAudioStreamUrl(videoUrl) ?: return Result.retry()

            // 2. Maak het lokale bestand aan
            val fileName = "${videoUrl.hashCode()}.m4a"
            val downloadsDir = File(applicationContext.getExternalFilesDir(null), "downloads")
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val outputFile = File(downloadsDir, fileName)

            // 3. Download de stream naar disk
            val request = Request.Builder().url(streamUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Result.retry()
                
                val body = response.body ?: return Result.failure()
                val totalBytes = body.contentLength()
                val artist = inputData.getString(KEY_ARTIST) ?: "Unknown Artist"
                val thumbnailUrl = inputData.getString(KEY_THUMBNAIL)
                val duration = inputData.getLong(KEY_DURATION, 0L)
                
                body.byteStream().use { inputStream ->
                    FileOutputStream(outputFile).use { outputStream ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var downloadedBytes = 0L
                        
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            
                            if (totalBytes > 0) {
                                setProgress(workDataOf(PROGRESS to (downloadedBytes * 100 / totalBytes).toInt()))
                            }
                        }
                    }
                }

                // 4. Opslaan in de database
                val db = AppDatabase.getDatabase(applicationContext)
                val track = OfflineTrack(
                    videoUrl = videoUrl,
                    title = title,
                    artist = artist,
                    thumbnailUrl = thumbnailUrl,
                    localPath = outputFile.absolutePath,
                    durationSeconds = duration
                )
                db.offlineTrackDao().insertTrack(track)
            }

            Log.d(TAG, "Download completed and saved to DB: ${outputFile.absolutePath}")
            Result.success(workDataOf(KEY_LOCAL_PATH to outputFile.absolutePath))
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "DownloadWorker"
        const val KEY_VIDEO_URL = "video_url"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_THUMBNAIL = "thumbnail"
        const val KEY_DURATION = "duration"
        const val KEY_LOCAL_PATH = "local_path"
        const val PROGRESS = "progress"
    }
}
