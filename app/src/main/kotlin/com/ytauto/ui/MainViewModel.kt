package com.ytauto.ui

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val youtubeRepo = YouTubeRepository()

    // ── UI States ──
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlayingState?>(null)
    val nowPlaying = _nowPlaying.asStateFlow()

    private val _queue = MutableStateFlow<List<MediaItem>>(emptyList())
    val queue = _queue.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _dominantColor = MutableStateFlow<Int?>(null)
    val dominantColor = _dominantColor.asStateFlow()

    private val _isVideoMode = MutableStateFlow(false)
    val isVideoMode = _isVideoMode.asStateFlow()

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var progressJob: Job? = null

    fun connectToService(context: Context) {
        if (mediaController != null) return
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
            setupPlayerListener()
            syncState()
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) { syncState() }
            override fun onPlaybackStateChanged(state: Int) { syncState() }
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) startProgressUpdate() else stopProgressUpdate()
            }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) { syncQueue() }
        })
    }

    private fun syncState() {
        val controller = mediaController ?: return
        val item = controller.currentMediaItem
        if (item != null) {
            _nowPlaying.value = NowPlayingState(
                title = item.mediaMetadata.title?.toString() ?: "Unknown",
                artist = item.mediaMetadata.artist?.toString() ?: "Unknown",
                artworkUri = item.mediaMetadata.artworkUri
            )
            _duration.value = controller.duration.coerceAtLeast(0L)
            syncQueue()
        }
    }

    private fun syncQueue() {
        val controller = mediaController ?: return
        val items = mutableListOf<MediaItem>()
        for (i in 0 until controller.mediaItemCount) {
            items.add(controller.getMediaItemAt(i))
        }
        val currentIndex = controller.currentMediaItemIndex
        _queue.value = if (currentIndex < items.size - 1) items.subList(currentIndex + 1, items.size) else emptyList()
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                mediaController?.let { 
                    _currentPosition.value = it.currentPosition
                    if (_duration.value <= 0) _duration.value = it.duration
                }
                delay(500) // Snellere update voor vloeiende slider
            }
        }
    }

    private fun stopProgressUpdate() { progressJob?.cancel() }

    fun playItem(result: SearchResult) {
        val controller = mediaController ?: return
        val results = _searchResults.value
        val mediaItems = results.map { it.toMediaItem() }
        val startIndex = results.indexOfFirst { it.videoUrl == result.videoUrl }.coerceAtLeast(0)

        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        controller.play()
    }

    fun skipToQueueItem(indexInQueue: Int) {
        val controller = mediaController ?: return
        val absoluteIndex = controller.currentMediaItemIndex + 1 + indexInQueue
        if (absoluteIndex < controller.mediaItemCount) {
            controller.seekToDefaultPosition(absoluteIndex)
            controller.play()
        }
    }

    fun onQueryChanged(q: String) { _searchQuery.value = q }
    fun search() {
        if (_searchQuery.value.isBlank()) return
        viewModelScope.launch {
            _isSearching.value = true
            try { _searchResults.value = youtubeRepo.search(_searchQuery.value) } catch (e: Exception) {}
            finally { _isSearching.value = false }
        }
    }

    fun togglePlayPause() {
        mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun seekTo(pos: Long) { mediaController?.seekTo(pos) }
    fun skipNext() { mediaController?.seekToNext() }
    fun skipPrevious() { mediaController?.seekToPrevious() }
    fun updateDominantColor(color: Int?) { _dominantColor.value = color }
    fun getController(): Player? = mediaController
    fun disconnectFromService() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }

    private fun SearchResult.toMediaItem() = MediaItem.Builder()
        .setMediaId(videoUrl)
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(title).setArtist(artist)
            .setArtworkUri(thumbnailUrl?.let { Uri.parse(it) })
            .setIsPlayable(true).build())
        .build()

    fun toggleVideoMode() {
        val newMode = !_isVideoMode.value
        _isVideoMode.value = newMode
        mediaController?.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.ACTION_TOGGLE_VIDEO_MODE, Bundle.EMPTY),
            Bundle().apply { putBoolean(PlaybackService.EXTRA_VIDEO_MODE, newMode) }
        )
    }
}

data class NowPlayingState(val title: String, val artist: String, val artworkUri: Uri?)
