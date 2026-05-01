# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

YTAuto is a private Android application that streams YouTube audio/video in Android Auto. It uses NewPipe Extractor (a reverse-engineered YouTube scraper) for privacy-first YouTube access without official API keys. The app is educational/personal — not intended for Play Store distribution.

**Language**: Kotlin only. **UI**: Jetpack Compose + Material 3. **Min SDK**: 26. **Target SDK**: 36.

## Build Commands

```bash
# Assemble debug APK
./gradlew assembleDebug

# Assemble release APK
./gradlew assembleRelease

# Run lint checks
./gradlew lint

# Clean build
./gradlew clean

# Install on connected device
./gradlew installDebug
```

There are no automated tests in this project. The CI workflow (`blank.yml`) is a placeholder and does not run real checks.

## Architecture

### Data Flow

```
YouTube (NewPipe) ──► YouTubeRepository ──► PlaybackService ──► ExoPlayer
                                                  │
                                             MediaSession ──► Android Auto
                                                  │
                                             MainViewModel ──► Compose UI
```

### Key Architectural Points

**`PlaybackService`** (`service/PlaybackService.kt`) is the heart of the app. It is a `MediaLibraryService` that:
- Hosts the `ExoPlayer` instance and `MediaLibrarySession`
- Resolves `MediaItem` URIs on demand in `onAddMediaItems` — `MediaItem`s are stored with their YouTube video URL as `mediaId`, and the actual stream URL is fetched just-in-time via `YouTubeRepository`
- Serves the Android Auto browse tree (4 categories: Recent, For You, Search Results, Offline Library)
- Manages audio effects (Equalizer, BassBoost, LoudnessEnhancer) and crossfade between tracks
- Hosts the `PartyServer` (Ktor/Netty HTTP server on port 8080)
- Handles SponsorBlock segment skipping via a 500ms polling loop

**`MainViewModel`** connects to `PlaybackService` via a `MediaController` (Media3's IPC mechanism). All player commands from the UI go through `mediaController`, not directly to the service.

**`YouTubeRepository`** wraps NewPipe Extractor. All calls are `suspend` functions running on `Dispatchers.IO` because NewPipe makes blocking HTTP calls. The `AppDownloader` class serves dual purpose: it is both the `Downloader` implementation required by NewPipe (registered in `YTAutoApp.onCreate`) and the utility for downloading audio files to local storage.

**Video mode vs Audio mode**: `isVideoModeEnabled` is a flag in `PlaybackService`. When toggled via the `ACTION_TOGGLE_VIDEO_MODE` custom session command, the service re-resolves the current item using `getVideoStreamUrl` vs `getAudioStreamUrl`. The toggle is sent from the UI via `MediaController.sendCustomCommand`.

### Room Database

`AppDatabase` (version 4, `fallbackToDestructiveMigration`) has three entities:
- `OfflineTrack` — downloaded files; `localPath` points to `context.filesDir/downloads/*.m4a`
- `RecentTrack` — play history, trimmed automatically via `addAndTrim`
- `PlayEvent` — analytics for the "For You" AI recommendations (top artists drive YouTube searches)

### Android Auto Integration

Two services are registered:
1. **`PlaybackService`** (registered as `MediaLibraryService` + `MediaBrowserService`) — the standard Media3 path for audio playback in Android Auto.
2. **`YTCarAppService`** (registered as `CarAppService` with category `NAVIGATION`) — the "video hack" that renders a `VideoPlayerScreen` in the Android Auto navigation surface using the Car App Library. `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` is intentionally used here.

### Shizuku Integration

`ShizukuManager` is a singleton that uses Shizuku (ADB-level access without root) to:
- Disable Android Auto driving restrictions via `settings put secure` ADB commands
- Force-whitelist the app in Google Play Services' phenotype database (SQLite hack on `com.google.android.gms`)

Shizuku is initialized in `YTAutoApp.onCreate` and permission is requested in `MainActivity.onCreate`.

### Party Mode

`PartyServer` (Ktor/Netty on port 8080) exposes a simple web UI at `/` and accepts track submissions at `/add?url=...`. Submitted URLs are passed to `PlaybackService` via a lambda callback. Spotify URLs are resolved by scraping the HTML `<title>` tag and searching YouTube for the result.

## Important Conventions

- **`mediaId` is always the YouTube video URL** (e.g. `https://www.youtube.com/watch?v=...`). Stream URLs are never stored as `mediaId`; they are resolved on demand in `PlaybackService.onAddMediaItems`. Offline tracks are the exception: when an offline track is played, the `MediaItem.uri` is overridden to `file://...` while `mediaId` remains the YouTube URL.
- **Audio effects are session-scoped**: `setupAudioEffects(sessionId)` must be called with the ExoPlayer audio session ID after the player is ready. Effects are released in `onDestroy`.
- **`@OptIn(UnstableApi::class)`** is required wherever Media3 `@UnstableApi` annotations are used (e.g., `DefaultLoadControl`, `PlayerView`, `AspectRatioFrameLayout`).
- **NewPipe requires initialization before first use**: `NewPipe.init(AppDownloader, Localization)` is called in `YTAutoApp.onCreate`. Never call NewPipe methods before this.
- **JitPack is required** for the NewPipe Extractor dependency — it is declared in `settings.gradle.kts` under `dependencyResolutionManagement`.
- The `packaging` block in `app/build.gradle.kts` excludes Netty's index files to prevent packaging conflicts.
