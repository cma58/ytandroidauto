package com.ytauto.ui

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.ViewModel
import java.net.Inet4Address
import java.net.NetworkInterface
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.ytauto.data.AppDownloader
import com.ytauto.data.SearchResult
import com.ytauto.data.YouTubeRepository
import com.ytauto.db.AppDatabase
import com.ytauto.service.PlaybackService
import com.ytauto.shizuku.ShizukuManager
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

    private val _bassBoostStrength = MutableStateFlow(800)
    val bassBoostStrength = _bassBoostStrength.asStateFlow()

    private val _loudnessGain = MutableStateFlow(1500)
    val loudnessGain = _loudnessGain.asStateFlow()

    private val _eqBands = MutableStateFlow(listOf(500, 200, -200, 300, 600))
    val eqBands = _eqBands.asStateFlow()

    private val _currentPreset = MutableStateFlow("Custom")
    val currentPreset = _currentPreset.asStateFlow()

    val presets = mapOf(
        "Standard" to AudioPreset(800, 1500, listOf(500, 200, -200, 300, 600)),
        "Bass Max" to AudioPreset(1000, 1200, listOf(800, 400, 0, 200, 400)),
        "Vocal" to AudioPreset(300, 1800, listOf(-200, 0, 800, 400, -200)),
        "Flat" to AudioPreset(0, 0, listOf(0, 0, 0, 0, 0))
    )

    private val _downloadedUrls = MutableStateFlow<Set<String>>(emptySet())
    val downloadedUrls = _downloadedUrls.asStateFlow()

    private val _downloadedTracks = MutableStateFlow<List<com.ytauto.db.OfflineTrack>>(emptyList())
    val downloadedTracks = _downloadedTracks.asStateFlow()

    private val _recentTracks = MutableStateFlow<List<com.ytauto.db.RecentTrack>>(emptyList())
    val recentTracks = _recentTracks.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress = _downloadProgress.asStateFlow()

    val isShizukuAvailable = ShizukuManager.isAvailable
    val hasShizukuPermission = ShizukuManager.hasPermission

    fun refreshShizuku() {
        ShizukuManager.checkAvailability()
    }

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var progressJob: Job? = null

    fun connectToService(context: Context) {
        if (mediaController != null) return
        
        val downloader = AppDownloader.getInstance(context)
        
        // Laad gedownloade tracks
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(context)
            db.offlineTrackDao().getAllTracks().collect { tracks ->
                _downloadedTracks.value = tracks
                _downloadedUrls.value = tracks.map { it.videoUrl }.toSet()
            }
        }

        // Laad recente tracks
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(context)
            db.recentTrackDao().getAllRecentTracks().collect { tracks ->
                _recentTracks.value = tracks
            }
        }

        // Verzamel download progressie
        viewModelScope.launch {
            downloader.downloadProgress.collect { progress ->
                _downloadProgress.value = progress
            }
        }

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

    fun playOfflineTrack(track: com.ytauto.db.OfflineTrack) {
        val controller = mediaController ?: return
        val tracks = _downloadedTracks.value
        val mediaItems = tracks.map { it.toMediaItem() }
        val startIndex = tracks.indexOfFirst { it.videoUrl == track.videoUrl }.coerceAtLeast(0)

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

    fun downloadTrack(context: Context, result: SearchResult) {
        viewModelScope.launch {
            val downloader = AppDownloader.getInstance(context)
            downloader.downloadTrack(result.videoUrl)
        }
    }

    fun getController(): Player? = mediaController

    fun requestShizukuPermission() {
        ShizukuManager.requestPermission()
    }

    fun applyShizukuHacks() {
        viewModelScope.launch {
            ShizukuManager.disableDrivingRestrictions()
        }
    }

    fun disconnectFromService() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }

    fun playRecentTrack(track: com.ytauto.db.RecentTrack) {
        val controller = mediaController ?: return
        val tracks = _recentTracks.value
        val mediaItems = tracks.map { it.toMediaItem() }
        val startIndex = tracks.indexOfFirst { it.videoUrl == track.videoUrl }.coerceAtLeast(0)

        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        controller.play()
    }

    private fun SearchResult.toMediaItem() = MediaItem.Builder()
        .setMediaId(videoUrl)
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(title).setArtist(artist)
            .setArtworkUri(thumbnailUrl?.let { Uri.parse(it) })
            .setIsPlayable(true).build())
        .build()

    private fun com.ytauto.db.OfflineTrack.toMediaItem() = MediaItem.Builder()
        .setMediaId(videoUrl)
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(title).setArtist(artist)
            .setArtworkUri(thumbnailUrl?.let { Uri.parse(it) })
            .setIsPlayable(true).build())
        .build()

    private fun com.ytauto.db.RecentTrack.toMediaItem() = MediaItem.Builder()
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

    fun setBassBoost(strength: Int) {
        _bassBoostStrength.value = strength
        mediaController?.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.ACTION_SET_AUDIO_EFFECTS, Bundle.EMPTY),
            Bundle().apply { putInt(PlaybackService.EXTRA_BASS_BOOST, strength) }
        )
    }

    fun setLoudness(gain: Int) {
        _loudnessGain.value = gain
        mediaController?.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.ACTION_SET_AUDIO_EFFECTS, Bundle.EMPTY),
            Bundle().apply { putInt(PlaybackService.EXTRA_LOUDNESS, gain) }
        )
    }

    fun setEqBand(index: Int, level: Int) {
        val current = _eqBands.value.toMutableList()
        current[index] = level
        _eqBands.value = current
        _currentPreset.value = "Custom"
        mediaController?.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.ACTION_SET_AUDIO_EFFECTS, Bundle.EMPTY),
            Bundle().apply { 
                putInt(PlaybackService.EXTRA_EQ_BAND_INDEX, index)
                putInt(PlaybackService.EXTRA_EQ_BAND_LEVEL, level)
            }
        )
    }

    fun applyPreset(name: String) {
        val preset = presets[name] ?: return
        _currentPreset.value = name
        setBassBoost(preset.bass)
        setLoudness(preset.loudness)
        preset.eq.forEachIndexed { index, level ->
            // We roepen setEqBand aan maar overschrijven de preset naam niet telkens naar "Custom"
            val current = _eqBands.value.toMutableList()
            current[index] = level
            _eqBands.value = current
            mediaController?.sendCustomCommand(
                androidx.media3.session.SessionCommand(PlaybackService.ACTION_SET_AUDIO_EFFECTS, Bundle.EMPTY),
                Bundle().apply { 
                    putInt(PlaybackService.EXTRA_EQ_BAND_INDEX, index)
                    putInt(PlaybackService.EXTRA_EQ_BAND_LEVEL, level)
                }
            )
        }
        _currentPreset.value = name // Herstel naam na de loops
    }

    fun handleSharedText(text: String) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                // Simplistic conversion for common music platforms
                val searchQuery = if (text.contains("spotify.com") || text.contains("music.apple.com")) {
                    // Extract track info if possible, otherwise use whole text
                    // For now, let's try to extract something between last / and ?
                    text.substringAfterLast("/").substringBefore("?")
                } else {
                    text
                }
                
                val results = youtubeRepo.search(searchQuery)
                if (results.isNotEmpty()) {
                    playItem(results[0])
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error handling shared text", e)
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearAnalytics(context: Context) {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(context)
            db.playEventDao().clearAnalytics()
        }
    }

    fun getPartyModeUrl(): String {
        val ip = try {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress
        } catch (e: Exception) { null }
        return if (ip != null) "http://$ip:8080" else "http://<jouw-ip>:8080"
    }
}

data class AudioPreset(val bass: Int, val loudness: Int, val eq: List<Int>)

data class NowPlayingState(val title: String, val artist: String, val artworkUri: Uri?)
