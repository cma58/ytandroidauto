package com.ytauto.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

object SponsorBlockClient {
    private val client = OkHttpClient()

    // Extraheert de YouTube video ID uit een volledige URL of geeft de waarde terug als het al een ID is.
    private fun extractVideoId(videoUrl: String): String? {
        return when {
            videoUrl.contains("youtube.com/watch") ->
                android.net.Uri.parse(videoUrl).getQueryParameter("v")
            videoUrl.contains("youtu.be/") ->
                videoUrl.substringAfterLast("/").substringBefore("?")
            videoUrl.startsWith("http") -> null
            else -> videoUrl // al een raw video ID
        }
    }

    // Haalt de start- en eindtijden (in milliseconden) op van 'niet-muziek' stukken
    suspend fun getSkipSegments(videoUrl: String): List<Pair<Long, Long>> = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(videoUrl) ?: return@withContext emptyList()
        val segments = mutableListOf<Pair<Long, Long>>()
        try {
            val url = "https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=[\"music_offtopic\"]"
            val request = Request.Builder().url(url).build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonArray = JSONArray(response.body?.string() ?: "[]")
                    for (i in 0 until jsonArray.length()) {
                        val segment = jsonArray.getJSONObject(i).getJSONArray("segment")
                        val startMs = (segment.getDouble(0) * 1000).toLong()
                        val endMs = (segment.getDouble(1) * 1000).toLong()
                        segments.add(Pair(startMs, endMs))
                    }
                }
            }
        } catch (e: Exception) {
            // Foutje of geen skip-data? Geen probleem, we retourneren een lege lijst zodat de app nooit crasht.
        }
        return@withContext segments
    }
}
