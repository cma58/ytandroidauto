# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

YTAuto is a private Android application that streams YouTube audio/video in Android Auto. It uses NewPipe Extractor (a reverse-engineered YouTube scraper) for privacy-first YouTube access without official API keys. Educational/personal — not for Play Store distribution.

**Language**: Kotlin only. **UI**: Jetpack Compose + Material 3. **Min SDK**: 26. **Target SDK**: 36.

## Build Commands

```bash
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # release APK
./gradlew lint               # lint checks
./gradlew clean              # clean build
./gradlew installDebug       # install on connected device
```

No automated tests exist. The CI workflow (`blank.yml`) is a placeholder.

## Architecture

### Data Flow

```
YouTube (NewPipe) ──► YouTubeRepository ──► PlaybackService ──► ExoPlayer
                                                  │
                                             MediaSession ──► Android Auto
                                                  │
                                             MainViewModel ──► Compose UI
```

### Key Files & Responsibilities

| File | Responsibility |
|------|---------------|
| `service/PlaybackService.kt` | MediaLibraryService core — ExoPlayer, MediaSession, browse tree, audio effects, crossfade, SponsorBlock, PartyServer host |
| `service/YTCarAppService.kt` | CarAppService (NAVIGATION category) — entry point for video hack |
| `service/YTCarAppSession.kt` | Creates `VideoPlayerScreen` on car session start |
| `ui/car/VideoPlayerScreen.kt` | Renders NavigationTemplate + video surface in Android Auto |
| `ui/MainActivity.kt` | Compose host, share-intent handler, Shizuku init |
| `ui/MainViewModel.kt` | MediaController IPC bridge, all UI state (search, queue, audio effects, SponsorBlock, Party Mode URL) |
| `ui/SettingsScreen.kt` | EQ/BassBoost/Loudness sliders, SponsorBlock toggle, Shizuku hacks, Party Mode URL card |
| `data/YouTubeRepository.kt` | NewPipe wrapper — search, getAudioStreamUrl, getVideoStreamUrl, getVideoMetadata. Has 5-hour TTL stream URL cache |
| `data/AppDownloader.kt` | Dual-purpose: NewPipe `Downloader` implementation AND local file downloader. Singleton. Tracks download progress via StateFlow |
| `data/SponsorBlockClient.kt` | Fetches skip segments from sponsor.ajay.app. Extracts video ID from full YouTube URL before API call |
| `db/AppDatabase.kt` | Room v4, `fallbackToDestructiveMigration`, singleton |
| `db/OfflineTrack.kt` + `OfflineTrackDao.kt` | Downloaded files — `localPath` = `filesDir/downloads/*.m4a` |
| `db/RecentTrack.kt` + `RecentTrackDao.kt` | Play history, trimmed to 50 entries via `addAndTrim` |
| `db/Analytics.kt` | `PlayEvent` entity + `PlayEventDao` — top artists drive "For You" recommendations |
| `remote/PartyServer.kt` | Ktor/Netty server on port 8080. `/` = web UI, `/add?url=` = enqueue track. Spotify URLs resolved via HTML title scraping |
| `shizuku/ShizukuManager.kt` | Singleton. ADB-level hacks: disable driving restrictions, inject into GMS phenotype.db whitelist |
| `worker/DownloadWorker.kt` | WorkManager background sync — seeds from random offline track artist, downloads 5 related tracks. WiFi-only constraint required |
| `receiver/BootReceiver.kt` | On boot: init Shizuku + enqueue DownloadWorker with `NetworkType.UNMETERED` constraint |
| `receiver/BluetoothReceiver.kt` | On car Bluetooth connect (AUDIO_VIDEO_CAR_AUDIO class): sends ACTION_AUTO_PLAY to PlaybackService |
| `YTAutoApp.kt` | Application class — initializes NewPipe + Shizuku |

### Room Database Schema (version 4)

- **`offline_tracks`**: `videoUrl` (PK), `title`, `artist`, `thumbnailUrl`, `localPath`, `durationSeconds`, `downloadedAt`, `isFavorite`, `playCount`
- **`recent_tracks`**: `videoUrl` (PK), `title`, `artist`, `thumbnailUrl`, `durationSeconds`, `lastPlayedAt`
- **`play_events`**: `id` (PK autoincrement), `mediaId`, `title`, `artist`, `timestamp`

### Android Auto Integration

Two services registered in manifest:
1. **`PlaybackService`** — `MediaLibraryService` + `MediaBrowserService` intent filters. Standard Media3 audio path. Browse tree: Recent (DB), For You (AI from PlayEvent analytics), Search Results (in-memory cache), Offline Library (DB).
2. **`YTCarAppService`** — `CarAppService` with `NAVIGATION` category. Renders video on car surface via `SurfaceCallback`. Uses `ALLOW_ALL_HOSTS_VALIDATOR` intentionally.

### Custom Session Commands (PlaybackService ↔ MainViewModel)

| Action constant | Extra keys | Effect |
|-----------------|-----------|--------|
| `ACTION_TOGGLE_VIDEO_MODE` | `EXTRA_VIDEO_MODE: Boolean` | Re-resolves current item with video/audio URL, updates custom layout button |
| `ACTION_SET_AUDIO_EFFECTS` | `EXTRA_BASS_BOOST`, `EXTRA_LOUDNESS`, `EXTRA_EQ_BAND_INDEX` + `EXTRA_EQ_BAND_LEVEL` | Adjusts live audio effect instances |
| `ACTION_SET_SPONSORBLOCK` | `EXTRA_SPONSORBLOCK_ENABLED: Boolean` | Enables/disables SponsorBlock segment skipping in service |

### Audio Effects

`setupAudioEffects(sessionId)` is called from `Player.Listener.onAudioSessionIdChanged` when ExoPlayer assigns a valid audio session ID. It initializes `LoudnessEnhancer` (15dB gain), `BassBoost` (strength 800), and `Equalizer` (V-curve preset). Effects are released in `onDestroy`.

### Video Mode Toggle

`isVideoModeEnabled` flag in `PlaybackService`. On toggle: `refreshCurrentItem()` re-fetches stream URL (audio vs video), then `setCustomLayout()` updates the Android Auto button icon (videocam ↔ audiotrack). Icons: `res/drawable/ic_videocam_car.xml`, `res/drawable/ic_audiotrack_car.xml`.

### VideoPlayerScreen Race Condition Fix

`pendingSurface` stores the Android Auto surface when `onSurfaceAvailable` fires. It is applied to the `MediaController` once the async `buildAsync()` completes (in the `controllerFuture` listener), because the surface often arrives before the controller is ready.

### Stream URL Caching

`YouTubeRepository` maintains an in-memory `streamUrlCache: Map<String, Pair<String, Long>>`. Keys are prefixed: `"a:$videoUrl"` for audio, `"v:$videoUrl"` for video. TTL = 5 hours (YouTube stream URLs are valid ~6 hours). Cache is instance-scoped — lost on service restart.

### Party Mode

`PartyServer` is started in `PlaybackService.onCreate()` and stopped in `onDestroy()`. The local IP + port are resolved in `MainViewModel.loadPartyModeUrl()` on `Dispatchers.IO` and exposed as `partyModeUrl: StateFlow<String>`. Called from `SettingsScreen` via `LaunchedEffect(Unit)`.

### SponsorBlock

`SponsorBlockClient.getSkipSegments(videoUrl)` extracts the YouTube video ID from the full URL before calling the API. Segments are fetched on `onMediaItemTransition`, stored in `currentSkipSegments`, and checked every 500ms in `startPositionTracking()`. Skipping is controlled by `isSponsorBlockEnabled` in `PlaybackService`, toggled via `ACTION_SET_SPONSORBLOCK` command.

## Important Conventions

- **`mediaId` is always the full YouTube video URL** (`https://www.youtube.com/watch?v=...`). Stream URLs are never stored as `mediaId`; resolved on demand in `onAddMediaItems`. Offline tracks override `MediaItem.uri` to `file://...` while keeping the YouTube URL as `mediaId`.
- **`@OptIn(UnstableApi::class)`** required for `DefaultLoadControl`, `PlayerView`, `AspectRatioFrameLayout`, `CommandButton.Builder.setIconResId`, `onConnect`/`onCustomCommand` overrides.
- **NewPipe must be initialized before first use** — done in `YTAutoApp.onCreate()`. Never call NewPipe before this.
- **JitPack** required for NewPipe Extractor — declared in `settings.gradle.kts`.
- **`packaging` block** in `app/build.gradle.kts` excludes `/META-INF/INDEX.LIST` and `/META-INF/io.netty.versions.properties` to prevent Netty packaging conflicts.
- **`AppDownloader` is a singleton** — call `AppDownloader.getInstance(context)`. It is also registered with NewPipe in `YTAutoApp.onCreate()`.
- **`AppDatabase` is a singleton** — call `AppDatabase.getDatabase(context)`.
- **`ShizukuManager` is a singleton object** — initialized in `YTAutoApp.onCreate()`, permission requested in `MainActivity.onCreate()`.

## Known Remaining TODOs

- **Settings not persisted**: `autoSyncEnabled` in `SettingsScreen` is still local `remember` state — resets on app restart. Should use DataStore.
- **`searchResultsCache` in `PlaybackService`** has no max size — can grow unboundedly in long sessions.
- **`autoSyncEnabled` toggle** is not connected to WorkManager scheduling yet.
- **Stream URL cache** is instance-scoped — lost when `PlaybackService` is destroyed/recreated.

## Dependencies (key)

```
media3 = 1.5.1       # ExoPlayer, MediaSession, MediaLibraryService
compose-bom = 2024.12.01
room = 2.7.0-alpha11  # kapt for annotation processing
ktor = 2.3.12        # PartyServer (Netty engine)
newpipe-extractor = v0.26.1  # via JitPack
shizuku = 13.1.0
car-app = 1.7.0      # app + app-projected (NOT app-automotive)
coil = 2.7.0         # thumbnail loading
work = 2.10.0        # DownloadWorker
```
