package com.ytauto.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.ytauto.data.SearchResult
import com.ytauto.data.YouTubeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * PlaybackService — De kern van de app
 *
 * Dit is een [MediaLibraryService] die:
 *
 * 1. **ExoPlayer** configureert voor audio-only afspelen
 * 2. **MediaLibrarySession** host die communicatie met controllers
 *    (Android Auto, notificatie, Compose UI) mogelijk maakt
 * 3. **Browse-boom** levert aan Android Auto met zoekresultaten
 * 4. **Foreground notification** beheert automatisch via Media3
 * 5. **Audio-URL's** resolved wanneer een gebruiker een item selecteert
 *
 * === Android 16 Compliance ===
 * - foregroundServiceType="mediaPlayback" in manifest
 * - FOREGROUND_SERVICE + FOREGROUND_SERVICE_MEDIA_PLAYBACK permissies
 * - Service wordt automatisch foreground bij afspelen via Media3
 * - Notification is gekoppeld aan de service lifecycle
 */
class PlaybackService : MediaLibraryService() {

    // ── Velden ──
    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private val youtubeRepo = YouTubeRepository()

    // Coroutine scope voor achtergrondwerk (YouTube zoeken, URL resolven)
    // SupervisorJob zorgt ervoor dat één gefaalde coroutine niet alles stopt
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Cache van de laatste zoekresultaten per query
    private val searchResultsCache = mutableMapOf<String, List<SearchResult>>()
    // De laatst uitgevoerde zoekquery
    private var lastSearchQuery: String = ""

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PlaybackService onCreate")

        // Maak het notificatiekanaal aan (vereist vanaf Android 8)
        createNotificationChannel()

        // ── ExoPlayer configureren ──
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                // handleAudioFocus = true: ExoPlayer pauzeert automatisch
                // bij telefoonoproepen, navigatie-aanwijzingen, etc.
                /* handleAudioFocus = */ true
            )
            // Pauzeer als de gebruiker de koptelefoon uittrekt
            .setHandleAudioBecomingNoisy(true)
            .build()

        // ── MediaLibrarySession aanmaken ──
        // Dit is het brein dat Android Auto, de notificatie, en de
        // mobiele UI met de ExoPlayer verbindt.
        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            player,
            librarySessionCallback
        )
            .build()
    }

    /**
     * Geeft de sessie terug aan verbindende controllers.
     * Android Auto en de mobiele UI roepen dit aan om toegang te krijgen.
     */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        Log.d(TAG, "PlaybackService onDestroy")

        // Ruim alles netjes op om memory leaks te voorkomen
        mediaLibrarySession?.run {
            player.release()
            release()
        }
        mediaLibrarySession = null
        serviceScope.cancel()

        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Als de gebruiker de app wegveegt en er niets afspeelt,
        // stop dan de service om resources vrij te geven.
        val player = mediaLibrarySession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // NOTIFICATION CHANNEL
    // ═══════════════════════════════════════════════════════════════

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(com.ytauto.R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW // LOW = geen geluid
            ).apply {
                description = getString(com.ytauto.R.string.notification_channel_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // MEDIA LIBRARY SESSION CALLBACK
    //
    // Dit is waar de magie gebeurt voor Android Auto.
    // Elke methode correspondeert met een actie die een browser
    // (Android Auto, Compose UI) kan uitvoeren.
    // ═══════════════════════════════════════════════════════════════

    private val librarySessionCallback = object : MediaLibrarySession.Callback {

        /**
         * Geeft de root van de browse-boom terug.
         *
         * Android Auto roept dit aan als eerste stap om de
         * mediabibliotheek te verkennen. De root zelf wordt niet
         * getoond — het is een onzichtbaar startpunt.
         */
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {

            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("YT Auto")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()

            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        /**
         * Geeft de kinderen van een browse-item terug.
         *
         * Android Auto roept dit aan wanneer de gebruiker een categorie opent.
         * We gebruiken dit om:
         * - Bij ROOT: de beschikbare categorieën te tonen
         * - Bij SEARCH_RESULTS: de zoekresultaten te tonen
         */
        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {

            return when (parentId) {
                ROOT_ID -> {
                    // Toon de categorieën onder de root
                    val categories = ImmutableList.of(
                        buildBrowsableItem(
                            SEARCH_RESULTS_ID,
                            "Zoekresultaten",
                            "Gebruik de zoekfunctie om muziek te vinden"
                        )
                    )
                    Futures.immediateFuture(LibraryResult.ofItemList(categories, params))
                }

                SEARCH_RESULTS_ID -> {
                    // Geef de gecachte zoekresultaten terug als MediaItems
                    val results = searchResultsCache[lastSearchQuery]
                        ?.map { it.toMediaItem() }
                        ?: emptyList()

                    Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(results), params)
                    )
                }

                else -> {
                    Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.of(), params)
                    )
                }
            }
        }

        /**
         * Wordt aangeroepen wanneer de gebruiker zoekt in Android Auto.
         *
         * We starten hier de YouTube-zoekopdracht asynchroon en sturen
         * een notificatie naar de browser zodra de resultaten klaar zijn.
         */
        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {

            Log.d(TAG, "onSearch: '$query'")
            lastSearchQuery = query

            // Start de zoekopdracht op een achtergrondthread
            serviceScope.launch {
                val results = youtubeRepo.search(query)
                searchResultsCache[query] = results

                // Informeer Android Auto dat de zoekresultaten klaar zijn.
                // Dit triggert onGetSearchResult() of een UI-update.
                session.notifySearchResultChanged(browser, query, results.size, params)
            }

            // Stuur direct een succesvolle response — resultaten volgen later
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        /**
         * Geeft de zoekresultaten terug nadat [onSearch] klaar is.
         *
         * Android Auto roept dit aan na de notifySearchResultChanged() call.
         */
        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {

            val results = searchResultsCache[query]
                ?.map { it.toMediaItem() }
                ?: emptyList()

            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(results), params)
            )
        }

        /**
         * Wordt aangeroepen wanneer een gebruiker een item selecteert om af te spelen.
         *
         * Dit is het cruciale moment: het [MediaItem] dat we eerder gaven had
         * de YouTube video-URL als mediaId, maar GEEN afspeelbare URI.
         * Hier resolven we de daadwerkelijke audio-stream URL van YouTube
         * en geven een bijgewerkt MediaItem terug dat ExoPlayer kan afspelen.
         *
         * Dit werkt voor zowel Android Auto als de mobiele UI.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<List<MediaItem>> {

            val future = SettableFuture.create<List<MediaItem>>()

            serviceScope.launch {
                try {
                    val resolvedItems = mediaItems.map { item ->
                        val videoUrl = item.mediaId
                        Log.d(TAG, "Resolving audio URL for: $videoUrl")

                        // Haal de directe audio-stream URL op
                        val audioUrl = youtubeRepo.getAudioStreamUrl(videoUrl)

                        if (audioUrl != null) {
                            // Bouw een nieuw MediaItem met de echte audio-URI
                            item.buildUpon()
                                .setUri(Uri.parse(audioUrl))
                                .build()
                        } else {
                            Log.w(TAG, "Could not resolve audio for: $videoUrl")
                            item // Geef het originele item terug (zal falen bij afspelen)
                        }
                    }
                    future.set(resolvedItems)
                } catch (e: Exception) {
                    Log.e(TAG, "Error resolving media items", e)
                    future.setException(e)
                }
            }

            return future
        }

        /**
         * Bepaalt welke commando's beschikbaar zijn voor een controller.
         * We staan alle standaard commando's toe, plus zoeken.
         */
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val sessionCommands = connectionResult.availableSessionCommands
                .buildUpon()
                // Sta aangepaste commando's toe indien nodig
                .build()

            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                connectionResult.availablePlayerCommands
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Maakt een browsable (niet-afspeelbaar) MediaItem voor een categorie
     * in de Android Auto browse-boom.
     */
    private fun buildBrowsableItem(
        id: String,
        title: String,
        subtitle: String
    ): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    companion object {
        private const val TAG = "PlaybackService"

        // Notificatiekanaal ID (moet overeenkomen met wat Media3 verwacht)
        const val NOTIFICATION_CHANNEL_ID = "ytauto_playback"

        // Browse-boom ID's
        const val ROOT_ID = "[rootID]"
        const val SEARCH_RESULTS_ID = "[searchResultsID]"
    }
}

// ═══════════════════════════════════════════════════════════════
// EXTENSION FUNCTIONS
// ═══════════════════════════════════════════════════════════════

/**
 * Converteert een [SearchResult] naar een Media3 [MediaItem].
 *
 * Het MediaItem heeft:
 * - mediaId = YouTube video URL (wordt later geresolved naar audio-URL)
 * - metadata = titel, artiest, thumbnail, duur
 * - isPlayable = true (de gebruiker kan erop klikken om af te spelen)
 * - isBrowsable = false (het is een bladniveau item)
 *
 * De URI is NIET ingesteld — dat doen we in onAddMediaItems() wanneer
 * de gebruiker het item daadwerkelijk selecteert.
 */
private fun SearchResult.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setIsBrowsable(false)
        .setIsPlayable(true)

    // Stel de thumbnail in als artwork URI
    thumbnailUrl?.let { url ->
        metadata.setArtworkUri(Uri.parse(url))
    }

    return MediaItem.Builder()
        .setMediaId(videoUrl) // De YouTube URL als unieke ID
        .setMediaMetadata(metadata.build())
        .build()
}
