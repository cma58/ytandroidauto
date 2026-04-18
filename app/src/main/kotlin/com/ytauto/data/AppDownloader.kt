package com.ytauto.data

import android.content.Context
import android.util.Log
import com.ytauto.db.AppDatabase
import com.ytauto.db.OfflineTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AppDownloader — Verantwoordelijk voor het downloaden van YouTube streams naar lokale opslag
 * EN fungeert als de Downloader voor de NewPipe Extractor.
 */
class AppDownloader private constructor(private val context: Context) : Downloader() {

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress = _downloadProgress.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // --- NewPipe Downloader Implementatie ---

    @Throws(IOException::class)
    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
        val method = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val okHttpRequestBuilder = Request.Builder()
            .url(url)
            .method(method, dataToSend?.toRequestBody(null))

        headers.forEach { (key, values) ->
            values.forEach { value ->
                okHttpRequestBuilder.addHeader(key, value)
            }
        }

        val okHttpResponse = client.newCall(okHttpRequestBuilder.build()).execute()
        val responseBody = okHttpResponse.body?.string()

        return Response(
            okHttpResponse.code,
            okHttpResponse.message,
            okHttpResponse.headers.toMultimap(),
            responseBody,
            okHttpResponse.request.url.toString()
        )
    }

    // --- Bestaande download functionaliteit ---

    private val youtubeRepo = YouTubeRepository()
    private val offlineDao = AppDatabase.getDatabase(context).offlineTrackDao()

    /**
     * Downloadt een track naar de interne opslag en registreert deze in de database.
     */
    suspend fun downloadTrack(videoUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting download for: $videoUrl")
            
            // 1. Haal de stream URL en metadata op
            val streamUrl = youtubeRepo.getAudioStreamUrl(videoUrl) ?: return@withContext false
            val metadata = youtubeRepo.getVideoMetadata(videoUrl) ?: return@withContext false
            
            // 2. Bereid het lokale bestand voor
            val fileName = "${videoUrl.hashCode()}.m4a"
            val outputFile = File(context.filesDir, "downloads/$fileName")
            outputFile.parentFile?.mkdirs()

            // 3. Voer de werkelijke download uit
            val request = Request.Builder().url(streamUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                
                val body = response.body ?: return@withContext false
                val contentLength = body.contentLength()
                var bytesRead = 0L
                
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    val inputStream = body.byteStream()
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (contentLength > 0) {
                            val progress = bytesRead.toFloat() / contentLength
                            _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                                put(videoUrl, progress)
                            }
                        }
                    }
                }
            }

            // 4. Sla op in Room Database
            val offlineTrack = OfflineTrack(
                videoUrl = videoUrl,
                title = metadata.title,
                artist = metadata.artist,
                thumbnailUrl = metadata.thumbnailUrl,
                localPath = outputFile.absolutePath,
                durationSeconds = metadata.durationSeconds
            )
            offlineDao.insertTrack(offlineTrack)
            
            // Verwijder uit progress map na afronding
            _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                remove(videoUrl)
            }
            
            Log.d(TAG, "Download completed: ${metadata.title}")
            true
        } catch (e: Exception) {
            _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                remove(videoUrl)
            }
            Log.e(TAG, "Download failed for $videoUrl", e)
            false
        }
    }

    companion object {
        private const val TAG = "AppDownloader"

        @Volatile
        private var INSTANCE: AppDownloader? = null

        fun getInstance(context: Context): AppDownloader {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppDownloader(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
