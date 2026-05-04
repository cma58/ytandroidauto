package com.github.libretube.services

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.github.libretube.api.MediaServiceRepository
import com.github.libretube.api.TrendingCategory
import com.github.libretube.receivers.BluetoothReceiver
import com.github.libretube.db.DatabaseHolder
import com.github.libretube.extensions.toID
import com.github.libretube.helpers.PlayerHelper
import com.github.libretube.helpers.PreferenceHelper
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Auto media browser service.
 *
 * Browse tree:
 *   ROOT → Recently Watched (DB history), Trending, Music Trending
 *
 * Playback: stream URLs are resolved on-demand in [onAddMediaItems] so the
 * browse tree only carries lightweight metadata (no stream URLs stored).
 */
@OptIn(UnstableApi::class)
class AutoMediaLibraryService : AbstractPlayerService() {

    override val isOfflinePlayer: Boolean = false

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    // In-memory cache for the last search query results.
    private val searchResultsCache = mutableListOf<MediaItem>()

    // region AbstractPlayerService stubs — AA uses onAddMediaItems for playback instead
    override suspend fun onServiceCreated(args: Bundle) = Unit
    override suspend fun startPlayback() { super.startPlayback() }
    // endregion

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == BluetoothReceiver.ACTION_AUTO_PLAY) {
            val player = exoPlayer ?: return super.onStartCommand(intent, flags, startId)
            if (!player.isPlaying) {
                if (player.mediaItemCount > 0) {
                    if (player.playbackState == Player.STATE_IDLE) player.prepare()
                    player.play()
                } else {
                    serviceScope.launch {
                        val history = try {
                            DatabaseHolder.Database.watchHistoryDao().getN(20, 0)
                        } catch (e: Exception) { emptyList() }

                        val resolved = history.mapNotNull { item ->
                            resolveStreamItem(item.videoId, buildPlayableItem(item.videoId, item.title, item.uploader, item.thumbnailUrl))
                        }
                        if (resolved.isEmpty()) return@launch

                        withContext(Dispatchers.Main) {
                            player.setMediaItems(resolved)
                            player.shuffleModeEnabled = true
                            player.prepare()
                            player.play()
                        }
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    // region Android Auto browse

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(ROOT_ITEM, params))

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        serviceScope.launch {
            val items: ImmutableList<MediaItem> = when (parentId) {
                ROOT_ID -> ImmutableList.of(HISTORY_ITEM, TRENDING_ITEM, MUSIC_ITEM)
                HISTORY_ID -> loadHistory()
                TRENDING_ID -> loadTrending(null)
                MUSIC_ID -> loadTrending(TrendingCategory.MUSIC)
                else -> ImmutableList.of()
            }
            future.set(LibraryResult.ofItemList(items, params))
        }
        return future
    }

    override fun onSearch(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        serviceScope.launch {
            try {
                val result = MediaServiceRepository.instance.getSearchResults(query, "music_songs")
                val items = result.items.mapNotNull { item ->
                    if (item.type != "stream") return@mapNotNull null
                    val videoId = item.url.toID()
                    buildPlayableItem(videoId, item.title, item.uploaderName, item.thumbnail)
                }
                searchResultsCache.clear()
                searchResultsCache.addAll(items)
                session.notifySearchResultChanged(browser, query, items.size, params)
            } catch (e: Exception) {
                Log.e(TAG, "Search failed for '$query'", e)
            }
        }
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onGetSearchResult(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val snapshot = ImmutableList.copyOf(searchResultsCache)
        return Futures.immediateFuture(LibraryResult.ofItemList(snapshot, params))
    }

    // endregion

    // region Playback — resolve stream URLs on demand

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        val future = SettableFuture.create<List<MediaItem>>()
        serviceScope.launch {
            try {
                val resolved = mediaItems.mapNotNull { item ->
                    val videoId = item.mediaId.ifEmpty { return@mapNotNull null }
                    resolveStreamItem(videoId, item)
                }
                future.set(resolved)
                if (resolved.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        if (exoPlayer?.playbackState == Player.STATE_IDLE) exoPlayer?.prepare()
                        exoPlayer?.play()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "onAddMediaItems failed", e)
                future.setException(e)
            }
        }
        return future
    }

    private suspend fun resolveStreamItem(videoId: String, original: MediaItem): MediaItem? = try {
        val streams = MediaServiceRepository.instance.getStreams(videoId)
        val (uri, mimeType) = when {
            streams.videoStreams.isNotEmpty() -> {
                val dashUri = if (streams.isLive && streams.dash != null) {
                    streams.dash!!.toUri()
                } else {
                    PlayerHelper.createDashSource(streams, this@AutoMediaLibraryService)
                }
                dashUri to MimeTypes.APPLICATION_MPD
            }
            streams.hls != null -> streams.hls!!.toUri() to MimeTypes.APPLICATION_M3U8
            else -> {
                Log.w(TAG, "No stream found for $videoId")
                return null
            }
        }
        MediaItem.Builder()
            .setMediaId(videoId)
            .setUri(uri)
            .setMimeType(mimeType)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(streams.title)
                    .setArtist(streams.uploader)
                    .setArtworkUri(streams.thumbnailUrl.toUri())
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build()
            )
            .build()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to resolve stream for $videoId", e)
        null
    }

    // endregion

    // region Data loaders

    private suspend fun loadHistory(): ImmutableList<MediaItem> = try {
        val history = DatabaseHolder.Database.watchHistoryDao().getN(30, 0)
        ImmutableList.copyOf(history.mapNotNull { item ->
            buildPlayableItem(item.videoId, item.title, item.uploader, item.thumbnailUrl)
        })
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load history", e)
        ImmutableList.of()
    }

    private suspend fun loadTrending(category: TrendingCategory?): ImmutableList<MediaItem> = try {
        val region = PreferenceHelper.getTrendingRegion(this)
        val streams = if (category != null) {
            MediaServiceRepository.instance.getTrending(region, category)
        } else {
            MediaServiceRepository.instance.getTrending(region, TrendingCategory.MUSIC)
        }
        ImmutableList.copyOf(streams.take(30).mapNotNull { item ->
            val videoId = item.url?.toID() ?: return@mapNotNull null
            buildPlayableItem(videoId, item.title, item.uploaderName, item.thumbnail)
        })
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load trending", e)
        ImmutableList.of()
    }

    // endregion

    companion object {
        private const val TAG = "AutoMediaLibraryService"

        const val ROOT_ID = "[AA_ROOT]"
        const val HISTORY_ID = "[AA_HISTORY]"
        const val TRENDING_ID = "[AA_TRENDING]"
        const val MUSIC_ID = "[AA_MUSIC]"

        private val ROOT_ITEM = buildBrowsableItem(ROOT_ID, "LibreTube")
        private val HISTORY_ITEM = buildBrowsableItem(HISTORY_ID, "Recently Watched")
        private val TRENDING_ITEM = buildBrowsableItem(TRENDING_ID, "Trending")
        private val MUSIC_ITEM = buildBrowsableItem(MUSIC_ID, "Music")

        fun buildBrowsableItem(id: String, title: String): MediaItem = MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()

        fun buildPlayableItem(
            videoId: String,
            title: String?,
            artist: String?,
            thumbUrl: String?
        ): MediaItem = MediaItem.Builder()
            .setMediaId(videoId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(thumbUrl?.toUri())
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
    }
}
