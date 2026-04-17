package com.ytauto.ui

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.ytauto.data.SearchResult
import com.ytauto.data.YouTubeRepository
import com.ytauto.service.PlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * MainViewModel — ViewModel voor de mobiele Compose UI
 *
 * Beheert:
 * 1. **Zoekstatus**: query, resultaten, loading state
 * 2. **MediaController**: verbinding met de PlaybackService
 * 3. **Now Playing**: huidige track-informatie
 *
 * De MediaController is de "afstandsbediening" waarmee de UI
 * commando's stuurt naar de PlaybackService (play, pause, seek, etc.)
 */
class MainViewModel : ViewModel() {

    private val youtubeRepo = YouTubeRepository()

    // ── Zoekstatus ──
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ── Now Playing status ──
    private val _nowPlaying = MutableStateFlow<NowPlayingState?>(null)
    val nowPlaying: StateFlow<NowPlayingState?> = _nowPlaying.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // ── MediaController ──
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    // ═══════════════════════════════════════════════════════════════
    // MEDIACONTROLLER VERBINDING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Maakt verbinding met de PlaybackService via een MediaController.
     * Moet aangeroepen worden vanuit een Activity (heeft Context nodig).
     */
    fun connectToService(context: Context) {
        if (mediaController != null) return // Al verbonden

        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(context, sessionToken)
            .buildAsync()

        controllerFuture?.addListener(
            {
                try {
                    mediaController = controllerFuture?.get()
                    setupPlayerListener()
                    // Synchroniseer de huidige staat als er al iets afspeelt
                    syncNowPlaying()
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to connect to service", e)
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    /**
     * Verbreekt de verbinding met de service.
     * Moet aangeroepen worden wanneer de Activity stopt.
     */
    fun disconnectFromService() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController = null
        controllerFuture = null
    }

    /**
     * Luistert naar veranderingen in de player-staat.
     */
    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                syncNowPlaying()
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                syncNowPlaying()
            }
        })
    }

    /**
     * Synchroniseert de Now Playing status met de huidige player-staat.
     */
    private fun syncNowPlaying() {
        val controller = mediaController ?: return
        val currentItem = controller.currentMediaItem

        if (currentItem != null) {
            val metadata = currentItem.mediaMetadata
            _nowPlaying.value = NowPlayingState(
                title = metadata.title?.toString() ?: "Onbekend",
                artist = metadata.artist?.toString() ?: "Onbekend",
                artworkUri = metadata.artworkUri
            )
            _isPlaying.value = controller.isPlaying
        } else {
            _nowPlaying.value = null
            _isPlaying.value = false
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ZOEKEN
    // ═══════════════════════════════════════════════════════════════

    /**
     * Werkt de zoekquery bij (voor de zoekbalk UI).
     */
    fun onQueryChanged(query: String) {
        _searchQuery.value = query
    }

    /**
     * Voert een zoekopdracht uit op YouTube.
     */
    fun search() {
        val query = _searchQuery.value.trim()
        if (query.isBlank()) return

        viewModelScope.launch {
            _isSearching.value = true
            _errorMessage.value = null

            try {
                val results = youtubeRepo.search(query)
                _searchResults.value = results

                if (results.isEmpty()) {
                    _errorMessage.value = "Geen resultaten gevonden voor '$query'"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Zoeken mislukt: ${e.localizedMessage}"
                android.util.Log.e(TAG, "Search failed", e)
            } finally {
                _isSearching.value = false
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // AFSPELEN
    // ═══════════════════════════════════════════════════════════════

    /**
     * Speelt een zoekresultaat af.
     *
     * Stuurt een [MediaItem] naar de PlaybackService met de YouTube URL
     * als mediaId. De service resolved de audio-URL in onAddMediaItems().
     */
    fun playItem(result: SearchResult) {
        val controller = mediaController ?: return

        // Bouw het MediaItem met metadata (voor de notificatie en Auto)
        val mediaItem = MediaItem.Builder()
            .setMediaId(result.videoUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(result.title)
                    .setArtist(result.artist)
                    .setArtworkUri(result.thumbnailUrl?.let { Uri.parse(it) })
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build()
            )
            .build()

        // Stel het item in en begin met afspelen
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()

        // Update de UI direct (de listener update later met echte status)
        _nowPlaying.value = NowPlayingState(
            title = result.title,
            artist = result.artist,
            artworkUri = result.thumbnailUrl?.let { Uri.parse(it) }
        )
    }

    /**
     * Toggle play/pause.
     */
    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════════════

    override fun onCleared() {
        super.onCleared()
        disconnectFromService()
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}

/**
 * Staat van het huidige afspeelende item.
 */
data class NowPlayingState(
    val title: String,
    val artist: String,
    val artworkUri: Uri?
)
