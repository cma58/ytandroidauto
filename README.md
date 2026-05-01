# YTAuto – De Ultieme YouTube Media-ervaring voor Android Auto

YTAuto is een geavanceerde, privé Android-applicatie die YouTube-content naar je auto brengt met een focus op audio-kwaliteit, gebruiksgemak en slimme functies. In tegenstelling tot standaardoplossingen biedt YTAuto volledige controle over je luisterervaring, inclusief video-ondersteuning en systeem-hacks.

## 🚀 Belangrijkste Functies

- **YouTube Audio & Video**: Schakel naadloos tussen alleen audio (data-besparend) en volledige videoweergave op je telefoon of head-unit.
- **Android Auto Integratie**: Volledig gecertificeerde Media3-integratie voor een veilige en intuïtieve bediening tijdens het rijden.
- **Offline Bibliotheek**: Download je favoriete tracks voor gebruik zonder internetverbinding.
- **AI-Aanbevelingen (Smart Mixing)**: Een "Speciaal voor Jou" sectie met gepersonaliseerde mixen (bijv. Oujda Mix, Franse Rap) en dynamische aanbevelingen op basis van je luistergeschiedenis.
- **SponsorBlock**: Slaat automatisch gesponsorde segmenten in video's over voor een ononderbroken luisterervaring.
- **Pro Audio Engine**: 
  - 5-Bands Equalizer met presets (Bass Max, Vocal, etc.).
  - Krachtige Bass Boost en Loudness Enhancer speciaal getuned voor auto-speakers.
  - Naadloze crossfade tussen nummers met automatische volume-fades.
- **Systeem Hacks (Shizuku)**: Deactiveer automatisch Android Auto rij-restricties (ADB/Shizuku vereist) om volledige interface-toegang te behouden.
- **Share-to-Car**: Deel een link vanuit de YouTube- of Spotify-app direct naar YTAuto om het afspelen te starten.
- **Guest Casting (Party Mode)**: Ingebouwde webserver waarmee passagiers nummers aan de wachtrij kunnen toevoegen via hun eigen browser.

## 🛠️ Architectuur

- **UI**: Jetpack Compose met Material 3.
- **Media**: Media3 (ExoPlayer & MediaSession).
- **Data**: NewPipe Extractor voor privacy-vriendelijke YouTube toegang.
- **Database**: Room (Versie 4) voor offline opslag, geschiedenis en analytics.
- **Systeem**: Shizuku API voor geavanceerde systeem-modificaties.

## 📋 Vereisten

- Android SDK 26+ (Targeting SDK 36/Android 16).
- Android Studio Ladybug (2024.2+) of hoger.
- Shizuku-app (Optioneel, voor rij-restrictie hacks).

## 🔧 Installatie & Setup

1. **Clone de repository**:
   ```bash
   git clone https://github.com/jouw-gebruikersnaam/ytandroidauto.git
   ```
2. **Open in Android Studio**: Wacht tot de Gradle-sync is voltooid.
3. **Build & Run**: Installeer de `.apk` op je fysieke toestel.
4. **Shizuku Config**: Start de Shizuku-service en geef YTAuto toestemming in de instellingen voor de beste ervaring.

## ⚠️ Belangrijk

YTAuto is een **educatief project** en is niet bedoeld voor distributie via de Google Play Store. De app maakt gebruik van reverse-engineered API's via NewPipe; gebruik is op eigen risico.

---
*Ontwikkeld voor de ultieme roadtrip.*
