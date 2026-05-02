package com.ytauto.ui

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.ytauto.data.AppDownloader
import com.ytauto.data.SearchResult
import com.ytauto.data.SettingsRepository
import com.ytauto.data.UpdateChecker
import com.ytauto.data.UpdateInfo
import com.ytauto.data.YouTubeRepository
import com.ytauto.db.AppDatabase
import com.ytauto.service.PlaybackService
import com.ytauto.shizuku.ShizukuManager
import com.ytauto.worker.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val youtubeRepo = YouTubeRepository()
    private var db: AppDatabase? = null

    // ── UI States ──
    private val _selectedGenre = MutableStateFlow("Alles")
    val selectedGenre = _selectedGenre.asStateFlow()

    private val _forYouTracks = MutableStateFlow<List<SearchResult>>(emptyList())
    val forYouTracks = _forYouTracks.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed = _playbackSpeed.asStateFlow()
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

    // DataStore-backed settings — persisted across app restarts
    private var appContext: Context? = null

    private val _isSponsorBlockEnabled = MutableStateFlow(true)
    val isSponsorBlockEnabled = _isSponsorBlockEnabled.asStateFlow()

    private val _isAutoSyncEnabled = MutableStateFlow(true)
    val isAutoSyncEnabled = _isAutoSyncEnabled.asStateFlow()

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
        if (appContext == null) {
            appContext = context.applicationContext
            viewModelScope.launch {
                SettingsRepository.sponsorBlockEnabled(context).collect { _isSponsorBlockEnabled.value = it }
            }
            viewModelScope.launch {
                SettingsRepository.autoSyncEnabled(context).collect { _isAutoSyncEnabled.value = it }
            }
        }
        val downloader = AppDownloader.getInstance(context)
        val database = AppDatabase.getDatabase(context)
        this.db = database
        
        // Laad gedownloade tracks
        viewModelScope.launch {
            database.offlineTrackDao().getAllTracks().collect { tracks ->
                _downloadedTracks.value = tracks
                _downloadedUrls.value = tracks.map { it.videoUrl }.toSet()
            }
        }

        // Laad recente tracks
        viewModelScope.launch {
            database.recentTrackDao().getAllRecentTracks().collect { tracks ->
                _recentTracks.value = tracks
            }
        }

        // Laad recommendations gebaseerd op genre
        viewModelScope.launch {
            _selectedGenre.collect { genre ->
                val flow = if (genre == "Alles") {
                    database.recommendationCacheDao().getAllRecommendations()
                } else {
                    database.recommendationCacheDao().getRecommendationsByGenre(genre)
                }
                flow.collect { cached ->
                    _forYouTracks.value = cached.map { it.toSearchResult() }
                }
            }
        }

        // Initial Seeding
        viewModelScope.launch {
            if (database.recommendationCacheDao().getCount() == 0) {
                seedRecommendations()
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

    fun disconnectFromService() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController = null
        controllerFuture = null
        stopProgressUpdate()
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

        // Bouw de volledige zoekresultaten-lijst als wachtrij op, met het aangeklikte
        // item als startindex — zo blijft "Volgende" werken.
        val results = _searchResults.value
        val startIndex = results.indexOfFirst { it.videoUrl == result.videoUrl }.coerceAtLeast(0)
        val mediaItems = results.map { r ->
            val rMetadata = MediaMetadata.Builder()
                .setTitle(r.title)
                .setArtist(r.artist)
                .setArtworkUri(r.thumbnailUrl?.let { Uri.parse(it) })
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()

            MediaItem.Builder()
                .setMediaId(r.videoUrl)
                .setUri(Uri.parse(r.videoUrl))
                .setMediaMetadata(rMetadata)
                .build()
        }

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

    fun runCommand(command: String) {
        viewModelScope.launch {
            ShizukuManager.runCommand(command)
        }
    }

    fun toggleFavorite() {
        val current = _nowPlaying.value ?: return
        val context = appContext ?: return
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(context)
            db.playEventDao().insertEvent(
                com.ytauto.db.PlayEvent(
                    mediaId = current.title,
                    title = current.title,
                    artist = current.artist
                )
            )
            Log.d("Algorithm", "Favoriet opgeslagen voor: ${current.title}. Algoritme gevoed.")
            updateForYouList(current.artist)
        }
    }

    private fun updateForYouList(artist: String) {
        viewModelScope.launch {
            try {
                val recommendations = youtubeRepo.search("More from $artist", maxResults = 10)
                // Sla op in cache onder "Ontdekking"
                val cacheItems = recommendations.map { it.toCacheEntity("Ontdekking") }
                db?.recommendationCacheDao()?.insertAll(cacheItems)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error updating for you list", e)
            }
        }
    }

    private suspend fun seedRecommendations() {
        generateMix("Oujda")
        generateMix("Franse Rap")
        generateMix("Ontdekking")
    }

    fun generateMix(genre: String) {
        val query = when (genre) {
            "Oujda" -> "Oujda Marokkaanse muziek Rai Reggada 2024"
            "Franse Rap" -> "French Rap 2024 New"
            "Ontdekking" -> "Top Hits 2024 Discovery"
            else -> genre
        }
        viewModelScope.launch {
            try {
                val results = youtubeRepo.search(query, maxResults = 15)
                val cacheItems = results.map { it.toCacheEntity(genre) }
                db?.recommendationCacheDao()?.insertAll(cacheItems)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to generate mix for $genre", e)
            }
        }
    }

    fun setGenre(genre: String) {
        _selectedGenre.value = genre
    }

    private fun com.ytauto.db.RecommendationCache.toSearchResult() = SearchResult(
        videoUrl = videoUrl,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = 0 // Cache slaat dit niet op, maar search result heeft het wel nodig.
    )

    private fun SearchResult.toCacheEntity(genre: String) = com.ytauto.db.RecommendationCache(
        videoUrl = videoUrl,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        genre = genre
    )

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        mediaController?.setPlaybackSpeed(speed)
    }

    fun setSleepTimer(minutes: Int) {
        viewModelScope.launch {
            delay(minutes * 60 * 1000L)
            mediaController?.pause()
        }
    }

    fun toggleShuffle() {
        mediaController?.shuffleModeEnabled = !(mediaController?.shuffleModeEnabled ?: false)
    }

    fun toggleRepeat() {
        mediaController?.repeatMode = when (mediaController?.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
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

    private val _partyModeUrl = MutableStateFlow("Laden...")
    val partyModeUrl = _partyModeUrl.asStateFlow()

    fun loadPartyModeUrl() {
        viewModelScope.launch(Dispatchers.IO) {
            val ip = try {
                NetworkInterface.getNetworkInterfaces()
                    ?.toList()
                    ?.flatMap { it.inetAddresses.toList() }
                    ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                    ?.hostAddress
            } catch (e: Exception) { null }
            _partyModeUrl.value = if (ip != null) "http://$ip:8080" else "http://<jouw-ip>:8080"
        }
    }

    fun setSponsorBlock(enabled: Boolean) {
        _isSponsorBlockEnabled.value = enabled
        mediaController?.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.ACTION_SET_SPONSORBLOCK, Bundle.EMPTY),
            Bundle().apply { putBoolean(PlaybackService.EXTRA_SPONSORBLOCK_ENABLED, enabled) }
        )
        viewModelScope.launch {
            appContext?.let { SettingsRepository.setSponsorBlock(it, enabled) }
        }
    }

    // ── Auto-update ──────────────────────────────────────────────────────────

    sealed class UpdateState {
        object Idle : UpdateState()
        object Checking : UpdateState()
        object UpToDate : UpdateState()
        data class Available(val info: UpdateInfo) : UpdateState()
        data class Downloading(val progress: Float) : UpdateState()
        object ReadyToInstall : UpdateState()
        data class Error(val message: String) : UpdateState()
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState = _updateState.asStateFlow()

    fun checkForUpdate() {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            val latest = UpdateChecker.getLatestRelease()
            if (latest == null) {
                _updateState.value = UpdateState.Error("Kan niet verbinden met GitHub")
                return@launch
            }
            if (latest.versionCode > com.ytauto.BuildConfig.VERSION_CODE) {
                _updateState.value = UpdateState.Available(latest)
            } else {
                _updateState.value = UpdateState.UpToDate
            }
        }
    }

    fun downloadAndInstall(context: Context, info: UpdateInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            _updateState.value = UpdateState.Downloading(0f)
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder().url(info.downloadUrl).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body ?: run {
                        _updateState.value = UpdateState.Error("Download mislukt")
                        return@use
                    }
                    val contentLength = body.contentLength()
                    val outputFile = java.io.File(context.filesDir, "updates/update.apk")
                    outputFile.parentFile?.mkdirs()

                    var bytesRead = 0L
                    java.io.FileOutputStream(outputFile).use { out ->
                        val buffer = ByteArray(8192)
                        val input = body.byteStream()
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            bytesRead += read
                            if (contentLength > 0) {
                                _updateState.value = UpdateState.Downloading(
                                    bytesRead.toFloat() / contentLength
                                )
                            }
                        }
                    }

                    // Probeer stil installeren via Shizuku
                    val silentResult = if (ShizukuManager.hasPermission.value) {
                        ShizukuManager.runCommand("pm install -r ${outputFile.absolutePath}")
                    } else null

                    _updateState.value = if (
                        silentResult != null &&
                        silentResult.contains("Success", ignoreCase = true)
                    ) {
                        UpdateState.UpToDate // stil geïnstalleerd
                    } else {
                        UpdateState.ReadyToInstall // toon installer-dialog
                    }
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "Onbekende fout")
            }
        }
    }

    fun installApk(context: Context) {
        val apkFile = java.io.File(context.filesDir, "updates/update.apk")
        if (!apkFile.exists()) return
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apkFile
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun setAutoSync(context: Context, enabled: Boolean) {
        _isAutoSyncEnabled.value = enabled
        viewModelScope.launch {
            SettingsRepository.setAutoSync(context, enabled)
            if (enabled) {
                val request = PeriodicWorkRequestBuilder<DownloadWorker>(6, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.UNMETERED)
                            .build()
                    )
                    .build()
                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork("smart_sync", androidx.work.ExistingPeriodicWorkPolicy.KEEP, request)
            } else {
                WorkManager.getInstance(context).cancelUniqueWork("smart_sync")
            }
        }
    }
}

data class AudioPreset(val bass: Int, val loudness: Int, val eq: List<Int>)

data class NowPlayingState(val title: String, val artist: String, val artworkUri: Uri?)
