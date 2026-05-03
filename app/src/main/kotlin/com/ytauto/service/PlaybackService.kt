package com.ytauto.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.ytauto.R
import com.ytauto.ui.MainActivity
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.ytauto.data.SearchResult
import com.ytauto.data.YouTubeRepository
import com.ytauto.db.AppDatabase
import com.ytauto.db.RecentTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PlaybackService — De kern van de app
 */
class PlaybackService : MediaLibraryService() {

    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private val youtubeRepo = YouTubeRepository()

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    
    private var isCrossfading = false
    private var currentSkipSegments: List<Pair<Long, Long>> = emptyList()
    private var positionTrackingJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val CROSSFADE_DURATION_MS = 3000L

    private var isVideoModeEnabled = false
    private var isSponsorBlockEnabled = true
    // Maximaal 20 zoekopdrachten bijhouden; oudste verwijderen als de limiet bereikt is.
    private val searchResultsCache = object : LinkedHashMap<String, List<SearchResult>>(20, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, List<SearchResult>>) = size > 20
    }
    private var lastSearchQuery: String = ""
    private var partyServer: com.ytauto.remote.PartyServer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                32000, // Min buffer
                64000, // Max buffer
                500,   // Buffer voor afspelen (Gevraagde 500ms)
                1024   // Buffer na rebuffer
            )
            .build()

        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || 
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                    fadeIn()
                }

                serviceScope.launch {
                    val videoUrl = mediaItem?.mediaId
                    currentSkipSegments = if (isSponsorBlockEnabled &&
                        videoUrl != null &&
                        !videoUrl.startsWith("file://") &&
                        !videoUrl.startsWith("/")
                    ) {
                        com.ytauto.data.SponsorBlockClient.getSkipSegments(videoUrl)
                    } else {
                        emptyList()
                    }
                }

                mediaItem?.let { item ->
                    serviceScope.launch {
                        val db = AppDatabase.getDatabase(this@PlaybackService)
                        val recentTrack = RecentTrack(
                            videoUrl = item.mediaId,
                            title = item.mediaMetadata.title?.toString() ?: "Unknown",
                            artist = item.mediaMetadata.artist?.toString() ?: "Unknown",
                            thumbnailUrl = item.mediaMetadata.artworkUri?.toString(),
                            durationSeconds = player.duration / 1000
                        )
                        db.recentTrackDao().addAndTrim(recentTrack)

                        // Log PlayEvent for AI Analytics
                        db.playEventDao().insertEvent(
                            com.ytauto.db.PlayEvent(
                                mediaId = item.mediaId,
                                title = item.mediaMetadata.title?.toString() ?: "Unknown",
                                artist = item.mediaMetadata.artist?.toString()
                            )
                        )
                        
                        // Increment play count for offline track
                        db.offlineTrackDao().incrementPlayCount(item.mediaId)
                    }
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    setupAudioEffects(audioSessionId)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startPositionTracking()
                } else {
                    positionTrackingJob?.cancel()
                }
            }
        })

        val sessionActivity = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, librarySessionCallback)
            .setSessionActivity(sessionActivity)
            .setCustomLayout(listOf(buildVideoToggleButton()))
            .build()

        partyServer = com.ytauto.remote.PartyServer { url ->
            serviceScope.launch {
                handleIncomingUrl(url)
            }
        }.apply { start() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    private fun startPositionTracking() {
        positionTrackingJob?.cancel()
        positionTrackingJob = serviceScope.launch {
            while (isActive) {
                val pos = player.currentPosition
                if (player.isPlaying) {
                    // --- NIEUW: SPONSORBLOCK AUTO-SKIP ---
                    currentSkipSegments.find { pos in it.first..it.second }?.let {
                        player.seekTo(it.second + 100)
                    }
                    // --------------------------------

                    val remaining = player.duration - pos
                    if (remaining in 1..CROSSFADE_DURATION_MS && !isCrossfading && player.hasNextMediaItem()) {
                        fadeOutAndNext()
                    }
                }
                delay(500)
            }
        }
    }

    private fun fadeIn() {
        serviceScope.launch {
            if (isCrossfading) return@launch
            isCrossfading = true
            var vol = 0f
            player.volume = 0f
            val steps = 20
            for (i in 1..steps) {
                delay(CROSSFADE_DURATION_MS / steps)
                vol += 1f / steps
                player.volume = vol.coerceAtMost(1f)
            }
            player.volume = 1f
            isCrossfading = false
        }
    }

    private fun fadeOutAndNext() {
        serviceScope.launch {
            isCrossfading = true
            var vol = player.volume
            val steps = 20
            for (i in 1..steps) {
                delay(CROSSFADE_DURATION_MS / steps)
                vol -= 1f / steps
                player.volume = vol.coerceAtLeast(0f)
            }
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.play()
            }
            isCrossfading = false
        }
    }

    override fun onDestroy() {
        partyServer?.stop()
        loudnessEnhancer?.release()
        equalizer?.release()
        bassBoost?.release()
        positionTrackingJob?.cancel()
        mediaLibrarySession?.run {
            player.release()
            release()
        }
        mediaLibrarySession = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "YT Auto Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private val librarySessionCallback = object : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder().setMediaId(ROOT_ID)
                .setMediaMetadata(MediaMetadata.Builder().setTitle("YT Auto").setIsBrowsable(true).setIsPlayable(false).build()).build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return when (parentId) {
                ROOT_ID -> {
                    val categories = ImmutableList.of(
                        buildBrowsableItem(QUEUE_ID, "Huidige Wachtrij", "Bekijk wat hierna wordt afgespeeld"),
                        buildBrowsableItem(RECENT_TRACKS_ID, "Recent gespeeld", "Je laatst geluisterde nummers"),
                        buildBrowsableItem(FOR_YOU_ID, "Speciaal voor Jou", "AI-gegenereerde aanbevelingen"),
                        buildBrowsableItem(SEARCH_RESULTS_ID, "Zoekresultaten", "Gebruik de zoekfunctie"),
                        buildBrowsableItem(OFFLINE_TRACKS_ID, "Bibliotheek", "Gedownloade nummers")
                    )
                    Futures.immediateFuture(LibraryResult.ofItemList(categories, params))
                }
                FOR_YOU_ID -> {
                    val categories = ImmutableList.of(
                        buildBrowsableItem(FOR_YOU_GENRE_PREFIX + "Alles", "Alles", "Al je aanbevelingen"),
                        buildBrowsableItem(FOR_YOU_GENRE_PREFIX + "Aanbevolen", "Voor Jou", "Gabaseerd op je smaak"),
                        buildBrowsableItem(FOR_YOU_GENRE_PREFIX + "Oujda", "Oujda Mix", "Marokkaanse Rai & Reggada"),
                        buildBrowsableItem(FOR_YOU_GENRE_PREFIX + "Franse Rap", "Franse Rap Mix", "Franse scene & modern"),
                        buildBrowsableItem(FOR_YOU_GENRE_PREFIX + "Ontdekking", "Ontdekking", "Nieuwe muziek voor jou")
                    )
                    Futures.immediateFuture(LibraryResult.ofItemList(categories, params))
                }
                QUEUE_ID -> {
                    val mediaItems = mutableListOf<MediaItem>()
                    for (i in 0 until player.mediaItemCount) {
                        mediaItems.add(player.getMediaItemAt(i))
                    }
                    Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params))
                }
                RECENT_TRACKS_ID -> {
                    val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch {
                        val db = AppDatabase.getDatabase(this@PlaybackService)
                        val tracks = db.recentTrackDao().getAllRecentTracksOnce()
                        val mediaItems = tracks.map { it.toMediaItem() }
                        future.set(LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params))
                    }
                    future
                }
                SEARCH_RESULTS_ID -> {
                    val results = searchResultsCache[lastSearchQuery]?.map { it.toMediaItem() } ?: emptyList()
                    Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(results), params))
                }
                OFFLINE_TRACKS_ID -> {
                    val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch {
                        val db = AppDatabase.getDatabase(this@PlaybackService)
                        val tracks = db.offlineTrackDao().getAllTracksOnce()
                        val mediaItems = tracks.map { it.toMediaItem() }
                        future.set(LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params))
                    }
                    future
                }
                else -> {
                    if (parentId == FOR_YOU_GENRE_PREFIX + "Aanbevolen") {
                        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                        serviceScope.launch {
                            val db = AppDatabase.getDatabase(this@PlaybackService)
                            val topArtists = db.playEventDao().getTopArtists()
                            val recommendations = mutableListOf<MediaItem>()
                            
                            topArtists.forEach { artistCount ->
                                artistCount.artist?.let { artistName ->
                                    val results = youtubeRepo.search(artistName, maxResults = 3)
                                    recommendations.addAll(results.map { it.toMediaItem() })
                                }
                            }
                            
                            // If no analytics yet, show some defaults or empty
                            if (recommendations.isEmpty()) {
                                val results = youtubeRepo.search("Trending Music", maxResults = 10)
                                recommendations.addAll(results.map { it.toMediaItem() })
                            }

                            future.set(LibraryResult.ofItemList(ImmutableList.copyOf(recommendations.distinctBy { it.mediaId }), params))
                        }
                        future
                    } else if (parentId.startsWith(FOR_YOU_GENRE_PREFIX)) {
                        val genre = parentId.removePrefix(FOR_YOU_GENRE_PREFIX)
                        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                        serviceScope.launch {
                            val db = AppDatabase.getDatabase(this@PlaybackService)
                            val flow = if (genre == "Alles") {
                                db.recommendationCacheDao().getAllRecommendations()
                            } else {
                                db.recommendationCacheDao().getRecommendationsByGenre(genre)
                            }
                            // Collect once from flow
                            val cached = flow.first()
                            val mediaItems = cached.map { it.toMediaItem() }
                            future.set(LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params))
                        }
                        future
                    } else {
                        Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
                    }
                }
            }
        }

        override fun onSearch(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            lastSearchQuery = query
            serviceScope.launch {
                val results = youtubeRepo.search(query)
                searchResultsCache[query] = results
                session.notifySearchResultChanged(browser, query, results.size, params)
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, page: Int, pageSize: Int, params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val results = searchResultsCache[query]?.map { it.toMediaItem() } ?: emptyList()
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(results), params))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            val future = SettableFuture.create<List<MediaItem>>()
            serviceScope.launch {
                try {
                    val db = AppDatabase.getDatabase(this@PlaybackService)
                    val offlineDao = db.offlineTrackDao()
                    val deferredItems = mediaItems.map { item ->
                        async(Dispatchers.IO) {
                            // Handle potential search query from Voice Assistant
                            val searchQuery = item.requestMetadata.searchQuery
                            val mediaItemToResolve = if (!searchQuery.isNullOrEmpty()) {
                                val searchResults = youtubeRepo.search(searchQuery, maxResults = 1)
                                if (searchResults.isNotEmpty()) searchResults[0].toMediaItem() else null
                            } else {
                                item
                            }

                            if (mediaItemToResolve == null) return@async null

                            val offlineTrack = offlineDao.getTrackByUrl(mediaItemToResolve.mediaId)
                            if (offlineTrack != null && java.io.File(offlineTrack.localPath).exists()) {
                                return@async mediaItemToResolve.buildUpon().setUri(Uri.fromFile(java.io.File(offlineTrack.localPath))).build()
                            }
                            val streamUrl = if (isVideoModeEnabled) youtubeRepo.getVideoStreamUrl(mediaItemToResolve.mediaId) else youtubeRepo.getAudioStreamUrl(mediaItemToResolve.mediaId)
                            streamUrl?.let { mediaItemToResolve.buildUpon().setUri(Uri.parse(it)).build() }
                        }
                    }
                    future.set(deferredItems.awaitAll().filterNotNull())
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }

        @OptIn(UnstableApi::class)
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val defaultResult = super.onConnect(session, controller)
            val availableSessionCommands = defaultResult.availableSessionCommands.buildUpon()
                .add(SessionCommand(ACTION_TOGGLE_VIDEO_MODE, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SET_AUDIO_EFFECTS, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SET_SPONSORBLOCK, Bundle.EMPTY))
                .build()
            val playerCommands = defaultResult.availablePlayerCommands.buildUpon()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_STOP)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .build()
            // Include the custom layout so Android Auto receives it on connect
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(availableSessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .setCustomLayout(ImmutableList.of(buildVideoToggleButton()))
                .build()
        }

        @OptIn(UnstableApi::class)
        override fun onCustomCommand(
            session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_TOGGLE_VIDEO_MODE -> {
                    val newValue = args.getBoolean(EXTRA_VIDEO_MODE, !isVideoModeEnabled)
                    if (isVideoModeEnabled != newValue) {
                        isVideoModeEnabled = newValue
                        refreshCurrentItem()
                        // Use session parameter (guaranteed non-null) to update the button icon
                        session.setCustomLayout(listOf(buildVideoToggleButton()))
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_SET_AUDIO_EFFECTS -> {
                    val bass = args.getInt(EXTRA_BASS_BOOST, -1)
                    val loud = args.getInt(EXTRA_LOUDNESS, -1)
                    if (bass != -1) setBassBoost(bass)
                    if (loud != -1) setLoudness(loud)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_SET_SPONSORBLOCK -> {
                    isSponsorBlockEnabled = args.getBoolean(EXTRA_SPONSORBLOCK_ENABLED, true)
                    if (!isSponsorBlockEnabled) currentSkipSegments = emptyList()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }
    }

    private fun setBassBoost(strength: Int) {
        try {
            if (bassBoost == null) bassBoost = BassBoost(0, player.audioSessionId)
            bassBoost?.setStrength(strength.toShort())
            bassBoost?.enabled = strength > 0
        } catch (e: Exception) { Log.e("PlaybackService", "BassBoost error", e) }
    }

    private fun setLoudness(gainmB: Int) {
        try {
            if (loudnessEnhancer == null) loudnessEnhancer = LoudnessEnhancer(player.audioSessionId)
            loudnessEnhancer?.setTargetGain(gainmB)
            loudnessEnhancer?.enabled = gainmB > 0
        } catch (e: Exception) { Log.e("PlaybackService", "Loudness error", e) }
    }

    private fun setupAudioEffects(audioSessionId: Int) {
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(1500)
                enabled = true
            }
            bassBoost?.release()
            bassBoost = BassBoost(0, audioSessionId).apply {
                setStrength(800)
                enabled = true
            }
            equalizer?.release()
            equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
        } catch (e: Exception) {
            Log.e("PlaybackService", "AudioEffects init error", e)
        }
    }

    private suspend fun handleIncomingUrl(url: String) {
        val results = kotlinx.coroutines.withContext(Dispatchers.IO) {
            youtubeRepo.search(url, maxResults = 1)
        }
        if (results.isEmpty()) return
        player.addMediaItem(results[0].toMediaItem())
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        if (!player.isPlaying) player.play()
    }

    private fun refreshCurrentItem() {
        val current = player.currentMediaItem ?: return
        val position = player.currentPosition
        val index = player.currentMediaItemIndex
        // Capture before entering coroutine so metadata is never lost
        val mediaId = current.mediaId
        val savedMetadata = current.mediaMetadata
        serviceScope.launch {
            val streamUrl = withContext(Dispatchers.IO) {
                if (isVideoModeEnabled) youtubeRepo.getVideoStreamUrl(mediaId)
                else youtubeRepo.getAudioStreamUrl(mediaId)
            } ?: return@launch
            val newItem = MediaItem.Builder()
                .setMediaId(mediaId)
                .setUri(Uri.parse(streamUrl))
                .setMediaMetadata(savedMetadata)
                .build()
            player.replaceMediaItem(index, newItem)
            player.seekTo(position)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SET_AUDIO_EFFECTS) {
            val bass = intent.getIntExtra(EXTRA_BASS_BOOST, -1)
            val loud = intent.getIntExtra(EXTRA_LOUDNESS, -1)
            if (bass != -1) setBassBoost(bass)
            if (loud != -1) setLoudness(loud)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    @OptIn(UnstableApi::class)
    private fun buildVideoToggleButton(): CommandButton {
        val isVideo = isVideoModeEnabled
        return CommandButton.Builder()
            .setDisplayName(if (isVideo) "Audio modus" else "Video modus")
            .setSessionCommand(SessionCommand(ACTION_TOGGLE_VIDEO_MODE, Bundle.EMPTY))
            .setIconResId(if (isVideo) R.drawable.ic_audiotrack_car else R.drawable.ic_videocam_car)
            .build()
    }

    private fun buildBrowsableItem(id: String, title: String, subtitle: String): MediaItem {
        return MediaItem.Builder().setMediaId(id)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build())
            .build()
    }

    private fun SearchResult.toMediaItem() = MediaItem.Builder()
        .setMediaId(videoUrl)
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(title).setArtist(artist)
            .setArtworkUri(thumbnailUrl?.let { Uri.parse(it) })
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build())
        .build()

    private fun com.ytauto.db.OfflineTrack.toMediaItem() = MediaItem.Builder()
        .setMediaId(videoUrl)
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(title).setArtist(artist)
            .setArtworkUri(thumbnailUrl?.let { Uri.parse(it) })
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build())
        .build()

    private fun com.ytauto.db.RecentTrack.toMediaItem() = MediaItem.Builder()
        .setMediaId(videoUrl)
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(title).setArtist(artist)
            .setArtworkUri(thumbnailUrl?.let { Uri.parse(it) })
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build())
        .build()

    private fun com.ytauto.db.RecommendationCache.toMediaItem() = MediaItem.Builder()
        .setMediaId(videoUrl)
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(title).setArtist(artist)
            .setArtworkUri(thumbnailUrl?.let { Uri.parse(it) })
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build())
        .build()

    companion object {
        const val ACTION_TOGGLE_VIDEO_MODE = "com.ytauto.ACTION_TOGGLE_VIDEO_MODE"
        const val EXTRA_VIDEO_MODE = "extra_video_mode"
        const val ACTION_SET_AUDIO_EFFECTS = "com.ytauto.ACTION_SET_AUDIO_EFFECTS"
        const val EXTRA_BASS_BOOST = "extra_bass_boost"
        const val EXTRA_LOUDNESS = "extra_loudness"
        const val EXTRA_EQ_BAND_INDEX = "extra_eq_band_index"
        const val EXTRA_EQ_BAND_LEVEL = "extra_eq_band_level"
        const val NOTIFICATION_CHANNEL_ID = "ytauto_playback"
        const val ROOT_ID = "root"
        const val QUEUE_ID = "[queueID]"
        const val SEARCH_RESULTS_ID = "[searchResultsID]"
        const val OFFLINE_TRACKS_ID = "[offlineTracksID]"
        const val RECENT_TRACKS_ID = "[recentTracksID]"
        const val FOR_YOU_ID = "[forYouID]"
        const val ACTION_SET_SPONSORBLOCK = "com.ytauto.ACTION_SET_SPONSORBLOCK"
        const val EXTRA_SPONSORBLOCK_ENABLED = "extra_sponsorblock_enabled"
        const val FOR_YOU_GENRE_PREFIX = "[forYouGenre]_"
    }
}
