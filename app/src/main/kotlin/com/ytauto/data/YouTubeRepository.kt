package com.ytauto.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * YouTubeRepository — Data-laag voor YouTube interactie
 *
 * Verantwoordelijk voor:
 * 1. Zoeken op YouTube via NewPipe Extractor
 * 2. Ophalen van metadata (titel, kanaal, thumbnail)
 * 3. Extraheren van de directe audio-stream URL
 *
 * Alle operaties worden op [Dispatchers.IO] uitgevoerd om de main thread
 * niet te blokkeren. NewPipe Extractor doet synchrone HTTP-calls,
 * dus coroutines met IO dispatcher zijn essentieel.
 */
class YouTubeRepository {

    private val youtubeService = ServiceList.YouTube

    // Stream-URL cache: videoUrl -> Pair(streamUrl, expiryTimeMs)
    // YouTube stream-URLs zijn ~6 uur geldig; we cachen ze 5 uur.
    private val streamUrlCache = mutableMapOf<String, Pair<String, Long>>()
    private val CACHE_TTL_MS = 5 * 60 * 60 * 1000L

    /**
     * Zoekt op YouTube en geeft een lijst van [SearchResult] objecten terug.
     *
     * @param query De zoekterm (bijv. "Bohemian Rhapsody")
     * @param maxResults Maximaal aantal resultaten om terug te geven
     * @return Lijst met zoekresultaten inclusief metadata
     */
    suspend fun search(query: String, maxResults: Int = 15): List<SearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                // Maak een zoek-query handler voor YouTube
                val queryHandler = youtubeService
                    .searchQHFactory
                    .fromQuery(query)

                // Voer de zoekopdracht uit
                val searchInfo = SearchInfo.getInfo(youtubeService, queryHandler)

                // Filter alleen op video's (geen kanalen, playlists, etc.)
                // en map naar onze eigen SearchResult klasse
                searchInfo.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .take(maxResults)
                    .map { item ->
                        SearchResult(
                            videoUrl = item.url,
                            title = item.name,
                            artist = item.uploaderName ?: "Onbekend",
                            thumbnailUrl = item.thumbnails.firstOrNull()?.url,
                            durationSeconds = item.duration
                        )
                    }
            } catch (e: Exception) {
                // Log de fout en geef een lege lijst terug
                android.util.Log.e(TAG, "Search failed for: $query", e)
                emptyList()
            }
        }
    }

    /**
     * Haalt de directe audio-stream URL op voor een YouTube-video.
     *
     * Selecteert de audio-stream met de hoogste bitrate in M4A/WebM formaat.
     * ExoPlayer kan beide formaten native afspelen.
     *
     * @param videoUrl De volledige YouTube video URL (bijv. "https://www.youtube.com/watch?v=...")
     * @return De directe URL naar de audio-stream, of null als er geen gevonden wordt
     */
    suspend fun getAudioStreamUrl(videoUrl: String): String? {
        streamUrlCache["a:$videoUrl"]?.let { (url, expiry) ->
            if (System.currentTimeMillis() < expiry) return url
        }
        return withContext(Dispatchers.IO) {
            try {
                val streamInfo = StreamInfo.getInfo(youtubeService, videoUrl)
                val audioStreams: List<AudioStream> = streamInfo.audioStreams

                if (audioStreams.isEmpty()) {
                    android.util.Log.w(TAG, "No audio streams found for: $videoUrl")
                    return@withContext null
                }

                val bestStream = audioStreams
                    .sortedWith(
                        compareByDescending<AudioStream> { stream ->
                            when {
                                stream.format?.mimeType?.contains("mp4") == true -> 1
                                stream.format?.mimeType?.contains("webm") == true -> 0
                                else -> -1
                            }
                        }.thenByDescending { it.averageBitrate }
                    )
                    .first()

                android.util.Log.d(TAG, "Selected stream: ${bestStream.format?.mimeType}, ${bestStream.averageBitrate}kbps")

                bestStream.content?.also { url ->
                    streamUrlCache["a:$videoUrl"] = Pair(url, System.currentTimeMillis() + CACHE_TTL_MS)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to get audio URL for: $videoUrl", e)
                null
            }
        }
    }

    /**
     * Haalt de directe VIDEO-stream URL op voor een YouTube-video.
     * Kiest bij voorkeur een stream die zowel video als audio bevat (muxed).
     */
    suspend fun getVideoStreamUrl(videoUrl: String): String? {
        streamUrlCache["v:$videoUrl"]?.let { (url, expiry) ->
            if (System.currentTimeMillis() < expiry) return url
        }
        return withContext(Dispatchers.IO) {
            try {
                val streamInfo = StreamInfo.getInfo(youtubeService, videoUrl)

                val bestMuxed = streamInfo.videoStreams
                    .filter { it.format?.mimeType?.contains("mp4") == true }
                    .maxByOrNull { it.resolution }

                val result = bestMuxed?.content
                    ?: streamInfo.videoStreams.firstOrNull()?.content

                result?.also { url ->
                    streamUrlCache["v:$videoUrl"] = Pair(url, System.currentTimeMillis() + CACHE_TTL_MS)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to get video URL for: $videoUrl", e)
                null
            }
        }
    }

    /**
     * Haalt volledige metadata op voor een video (voor de Now Playing weergave).
     *
     * @param videoUrl De YouTube video URL
     * @return Een [VideoMetadata] object met alle details
     */
    suspend fun getVideoMetadata(videoUrl: String): VideoMetadata? {
        return withContext(Dispatchers.IO) {
            try {
                val streamInfo = StreamInfo.getInfo(youtubeService, videoUrl)
                VideoMetadata(
                    title = streamInfo.name,
                    artist = streamInfo.uploaderName ?: "Onbekend",
                    thumbnailUrl = streamInfo.thumbnails.firstOrNull()?.url,
                    durationSeconds = streamInfo.duration
                )
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to get metadata for: $videoUrl", e)
                null
            }
        }
    }

    companion object {
        private const val TAG = "YouTubeRepository"
    }
}

// ═══════════════════════════════════════════════════════════════
// Data klassen
// ═══════════════════════════════════════════════════════════════

/**
 * Zoekresultaat van YouTube.
 * Bevat alle informatie die nodig is om een item weer te geven
 * in de UI of Android Auto browse-boom.
 */
data class SearchResult(
    val videoUrl: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long
) {
    /** Formatteert de duur als MM:SS of H:MM:SS */
    val formattedDuration: String
        get() {
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }
}

/**
 * Volledige metadata van een YouTube-video.
 */
data class VideoMetadata(
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long
)
