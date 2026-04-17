# YTAuto – YouTube Audio voor Android Auto

Een privé Android-app die YouTube-audio afspeelt en integreert met Android Auto.

## Vereisten

- Android Studio Ladybug (2024.2+) of nieuwer
- JDK 17
- Android SDK 36 (Android 16)
- Min SDK 26

## Project Setup

1. **Maak een nieuw Android project** in Android Studio:
   - Kies "Empty Activity" (Compose)
   - Package name: `com.ytauto`
   - Min SDK: 26, Target SDK: 36

2. **Vervang** de gegenereerde bestanden door de bestanden uit dit project.

3. **JitPack toevoegen** aan `settings.gradle.kts` (al gedaan in het bestand).

4. **Sync** het project en build.

## Architectuur

```
com.ytauto/
├── YTAutoApp.kt              ← Application class (NewPipe init)
├── data/
│   ├── AppDownloader.kt       ← OkHttp Downloader voor NewPipe
│   └── YouTubeRepository.kt   ← Zoek- en stream-extractie
├── service/
│   └── PlaybackService.kt     ← Media3 MediaLibraryService
├── ui/
│   ├── MainActivity.kt        ← Compose UI
│   ├── MainViewModel.kt       ← ViewModel
│   └── theme/Theme.kt         ← Material3 thema
```

## Android Auto testen

1. Installeer de app op je telefoon.
2. Open Android Auto (of de Desktop Head Unit emulator).
3. De app verschijnt in de mediabron-lijst.
4. Gebruik de zoekfunctie om nummers te vinden.

## Belangrijk

- Dit is een **privé-app**, niet bedoeld voor de Play Store.
- De app gebruikt NewPipe Extractor voor YouTube-data.
- Audio-URLs zijn tijdelijk en verlopen na enkele uren.
