package com.ytauto.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
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
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.ytauto.data.SearchResult
import com.ytauto.data.YouTubeRepository
import com.ytauto.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * PlaybackService — De kern van de app
 */
class PlaybackService : MediaLibraryService() {

    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private val youtubeRepo = YouTubeRepository()

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var isCrossfading = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var isVideoModeEnabled = false
    private val searchResultsCache = mutableMapOf<String, List<SearchResult>>()
    private var lastSearchQuery: String = ""

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
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    setupAudioEffects(audioSessionId)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || 
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                    applyCrossfade()
                }
            }
        })

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, librarySessionCallback).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    private fun setupAudioEffects(sessionId: Int) {
        try {
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                setTargetGain(1000)
                enabled = true
            }
            equalizer = Equalizer(0, sessionId).apply { enabled = true }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup audio effects", e)
        }
    }

    private fun applyCrossfade() {
        serviceScope.launch {
            if (isCrossfading) return@launch
            isCrossfading = true
            player.volume = 0f
            val steps = 20
            val duration = 2000L
            for (i in 1..steps) {
                kotlinx.coroutines.delay(duration / steps)
                player.volume = i.toFloat() / steps
            }
            player.volume = 1f
            isCrossfading = false
        }
    }

    override fun onDestroy() {
        loudnessEnhancer?.release()
        equalizer?.release()
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
                    val categories = ImmutableList.of(buildBrowsableItem(SEARCH_RESULTS_ID, "Zoekresultaten", "Gebruik de zoekfunctie"))
                    Futures.immediateFuture(LibraryResult.ofItemList(categories, params))
                }
                SEARCH_RESULTS_ID -> {
                    val results = searchResultsCache[lastSearchQuery]?.map { it.toMediaItem() } ?: emptyList()
                    Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(results), params))
                }
                else -> Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
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
                            val offlineTrack = offlineDao.getTrackByUrl(item.mediaId)
                            if (offlineTrack != null && java.io.File(offlineTrack.localPath).exists()) {
                                return@async item.buildUpon().setUri(Uri.fromFile(java.io.File(offlineTrack.localPath))).build()
                            }
                            val streamUrl = if (isVideoModeEnabled) youtubeRepo.getVideoStreamUrl(item.mediaId) else youtubeRepo.getAudioStreamUrl(item.mediaId)
                            streamUrl?.let { item.buildUpon().setUri(Uri.parse(it)).build() }
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
            val connectionResult = super.onConnect(session, controller)
            val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()
            availableSessionCommands.add(SessionCommand(ACTION_TOGGLE_VIDEO_MODE, Bundle.EMPTY))
            return MediaSession.ConnectionResult.accept(availableSessionCommands.build(), connectionResult.availablePlayerCommands)
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
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }

        private fun refreshCurrentItem() {
            val currentItem = player.currentMediaItem ?: return
            val currentPos = player.currentPosition
            val wasPlaying = player.isPlaying
            serviceScope.launch {
                val streamUrl = if (isVideoModeEnabled) youtubeRepo.getVideoStreamUrl(currentItem.mediaId) else youtubeRepo.getAudioStreamUrl(currentItem.mediaId)
                streamUrl?.let { url ->
                    player.setMediaItem(currentItem.buildUpon().setUri(Uri.parse(url)).build(), false)
                    player.prepare(); player.seekTo(currentPos)
                    if (wasPlaying) player.play()
                }
            }
        }

        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(emptyList(), C.INDEX_UNSET, C.TIME_UNSET))
        }
    }

    private fun buildBrowsableItem(id: String, title: String, subtitle: String): MediaItem {
        return MediaItem.Builder().setMediaId(id)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle).setIsBrowsable(true).setIsPlayable(false).build()).build()
    }

    companion object {
        private const val TAG = "PlaybackService"
        const val ACTION_TOGGLE_VIDEO_MODE = "com.ytauto.ACTION_TOGGLE_VIDEO_MODE"
        const val EXTRA_VIDEO_MODE = "extra_video_mode"
        const val NOTIFICATION_CHANNEL_ID = "ytauto_playback"
        const val ROOT_ID = "[rootID]"
        const val SEARCH_RESULTS_ID = "[searchResultsID]"
    }
}

private fun SearchResult.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder().setTitle(title).setArtist(artist).setIsBrowsable(false).setIsPlayable(true)
    thumbnailUrl?.let { metadata.setArtworkUri(Uri.parse(it)) }
    return MediaItem.Builder().setMediaId(videoUrl).setMediaMetadata(metadata.build()).build()
}
