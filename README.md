# YTAuto – De Ultieme YouTube Media-ervaring voor Android Auto

YTAuto is een geavanceerde, privé Android-applicatie die YouTube-content naar je auto brengt met een focus op audio-kwaliteit, gebruiksgemak en slimme functies. In tegenstelling tot standaardoplossingen biedt YTAuto volledige controle over je luisterervaring, inclusief video-ondersteuning en systeem-hacks.

## Functies

- **YouTube Audio & Video** — Schakel naadloos tussen alleen audio (data-besparend) en volledige videoweergave. In Android Auto verschijnt een video/audio-wisselknop direct in de Now Playing balk.
- **Android Auto Integratie** — Media3 MediaLibraryService met volledige Now Playing controls (play/pause, volgende, vorige) en een browse-boom met 4 categorieën: Recent, Speciaal voor Jou, Zoekresultaten en Bibliotheek.
- **Offline Bibliotheek** — Download tracks naar interne opslag voor gebruik zonder internet. Smart Auto-Sync downloadt automatisch nieuwe nummers via Wi-Fi op de achtergrond.
- **AI-Aanbevelingen** — "Speciaal voor Jou" sectie die leert van je luistergedrag (top artiesten uit afspeelgeschiedenis) en automatisch suggesties ophaalt.
- **Pro Audio Engine**
  - 5-Bands Equalizer met presets: Standard, Bass Max, Vocal, Flat.
  - Bass Boost en Loudness Enhancer getuned voor auto-speakers.
  - Naadloze crossfade tussen nummers (3 seconden).
- **SponsorBlock** — Slaat automatisch intro's en niet-muziek segmenten over in muziekvideo's. Aan/uit schakelbaar, instelling wordt opgeslagen.
- **Systeem Hacks (Shizuku)** — Deactiveer Android Auto rij-restricties via ADB zonder root. Injecteer de app in de Google Play Services whitelist voor volledige interface-toegang.
- **Share-to-Car** — Deel een YouTube- of Spotify-link vanuit elke app direct naar YTAuto om het afspelen te starten.
- **Party Mode** — Ingebouwde webserver op poort 8080. Passagiers kunnen via hun eigen browser (zelfde Wi-Fi) nummers toevoegen aan de wachtrij. Het adres is zichtbaar in Instellingen met een kopieerknop.

## Architectuur

| Laag | Technologie |
|------|------------|
| UI | Jetpack Compose + Material 3 |
| Media | Media3 (ExoPlayer & MediaLibrarySession) |
| Data | NewPipe Extractor — privacy-vriendelijke YouTube toegang zonder API-sleutel |
| Database | Room v4 — offline tracks, afspeelgeschiedenis, analytics |
| Instellingen | DataStore Preferences — SponsorBlock en Auto-Sync worden persistent opgeslagen |
| Achtergrond | WorkManager — Wi-Fi-only Smart Sync elke 6 uur |
| Systeem | Shizuku API voor ADB-niveau modificaties |

## Vereisten

- Android **SDK 26+** (target SDK 36 / Android 16)
- Android Studio **Ladybug (2024.2+)** of hoger
- Shizuku-app *(optioneel — voor rij-restrictie hacks)*

## Installatie & Build

```bash
# 1. Clone de repository
git clone https://github.com/cma58/ytandroidauto.git
cd ytandroidauto

# 2. Build debug APK
./gradlew assembleDebug

# APK staat op: app/build/outputs/apk/debug/app-debug.apk

# 3. Installeer op verbonden toestel
./gradlew installDebug
```

Of open het project in **Android Studio** en druk op de groene ▶ knop.

## Eerste gebruik

1. Start de app en open **Instellingen**
2. *(Optioneel)* Start Shizuku en druk op **"Injecteer in Android Auto"** voor volledige toegang
3. Verbind je telefoon met Android Auto
4. Zoek een nummer in de app of in Android Auto via de zoekfunctie
5. Gebruik de **video/audio-knop** in de Now Playing balk om te wisselen tussen streams

## ⚠️ Belangrijk

YTAuto is een **educatief project** en is niet bedoeld voor distributie via de Google Play Store. De app maakt gebruik van reverse-engineered API's via NewPipe; gebruik is op eigen risico.

---
*Ontwikkeld voor de ultieme roadtrip.*
