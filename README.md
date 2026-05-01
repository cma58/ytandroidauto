# YTAuto – De Ultieme YouTube Media-ervaring voor Android Auto

YTAuto is een geavanceerde, privé Android-applicatie die YouTube-content naar je auto brengt met een focus op audio-kwaliteit, gebruiksgemak en slimme functies. In tegenstelling tot standaardoplossingen biedt YTAuto volledige controle over je luisterervaring, inclusief video-ondersteuning en systeem-hacks.

---

## Functies

### YouTube Audio & Video
Speel elk YouTube-nummer af als pure audio of als volledige video. In **audio-modus** wordt alleen de geluidsstroom gedownload — dit spaart mobiele data en batterij. In **video-modus** wordt de volledige videostream afgespeeld, zichtbaar op je telefoonscherm of op de head-unit van je auto. Schakel tussen beide modi via de video/audio-knop in de Now Playing balk — de app herlaadt de stream automatisch zonder het nummer opnieuw te starten.

---

### Android Auto Integratie
YTAuto verschijnt als een volwaardige media-app in Android Auto. Vanuit het autodisplay kun je:
- Bladeren door **4 categorieën**: Recent gespeeld, Speciaal voor Jou, Zoekresultaten en Offline Bibliotheek.
- Nummers starten via de **spraakassistent** ("Speel [artiest] af").
- De standaard **Now Playing controls** gebruiken: play/pause, volgend nummer, vorig nummer.
- Wisselen tussen **audio en video** via een extra knop naast de standaardknoppen.

---

### Offline Bibliotheek
Download elk nummer naar de interne opslag van je telefoon zodat het afspeelt zonder internetverbinding — ideaal voor slecht bereik of data-besparing. Gedownloade nummers worden opgeslagen als M4A-audiobestand en zijn terug te vinden in de **Bibliotheek**-tab. De app houdt bij hoe vaak je elk nummer hebt afgespeeld en markeert favorieten.

---

### Smart Auto-Sync
De app kan automatisch nieuwe nummers downloaden op de achtergrond. Het systeem kijkt naar je opgeslagen nummers, pikt een willekeurige artiest eruit en zoekt 5 nieuwe gerelateerde tracks die het dan downloadt. Dit proces draait **alleen via Wi-Fi** en wordt elke 6 uur herhaald. De functie is aan/uit te zetten in Instellingen en de keuze wordt permanent opgeslagen.

---

### AI-Aanbevelingen — "Speciaal voor Jou"
Elke keer dat je een nummer afspeelt, slaat de app dit op als een afspeelgebeurtenis. Op basis van je **top 5 meest beluisterde artiesten** worden automatisch YouTube-zoekopdrachten uitgevoerd en de resultaten getoond in de "Speciaal voor Jou" sectie in Android Auto. Hoe meer je luistert, hoe persoonlijker de aanbevelingen worden. Afspeelgeschiedenis is te wissen via Instellingen.

---

### Pro Audio Engine

#### 5-Bands Equalizer
Pas de klank van het geluid aan met 5 frequentiebanden:
- **Band 0** — 60 Hz (diepe bas)
- **Band 1** — 230 Hz (warme bas)
- **Band 2** — 910 Hz (middentonen / stem)
- **Band 3** — 3,6 kHz (aanwezigheid)
- **Band 4** — 14 kHz (helderheid / lucht)

Kies uit vooringestelde presets (**Standard**, **Bass Max**, **Vocal**, **Flat**) of stel elke band handmatig in via de schuifjes in Instellingen.

#### Bass Boost
Versterkt de lage tonen extra hard via de hardware-audioproccessor van je telefoon. Speciaal afgesteld voor auto-luidsprekers die van nature minder bas produceren dan thuisluidsprekers.

#### Loudness Enhancer
Verhoogt het algemene volume met 15 dB zonder vervorming. Nuttig omdat YouTube-video's onderling sterk kunnen verschillen in volume — dit equaliseert het verschil automatisch.

#### Crossfade
Aan het einde van elk nummer begint het volgende nummer zacht in te faden terwijl het huidige uitfadet. Dit zorgt voor een vloeiende luisterervaring zonder stiltes tussen nummers.

---

### SponsorBlock
YouTube-muziekvideo's bevatten vaak intro's, outro's of gesproken stukken die niet bij de muziek horen. SponsorBlock is een open-source database van vrijwilligers die deze segmenten markeren. YTAuto haalt deze data automatisch op en **slaat de gemarkeerde stukken over** tijdens het afspelen. De functie is aan/uit te zetten in Instellingen en de keuze wordt opgeslagen zodat je hem niet telkens opnieuw hoeft in te stellen.

---

### Systeem Hacks via Shizuku

#### Wat is Shizuku?
Shizuku is een app die je telefoon tijdelijk ADB-rechten geeft zonder root. Dit geeft YTAuto toegang tot systeeminstellingen die normaal niet toegankelijk zijn.

#### Rij-restricties uitschakelen
Android Auto blokkeert bepaalde functies (zoals typen of video kijken) wanneer het detecteert dat de auto rijdt. Via Shizuku kan YTAuto deze beperkingen omzeilen zodat je de volledige interface behoudt tijdens het rijden.

#### Whitelist injectie
Android Auto heeft een interne lijst van goedgekeurde apps. Via Shizuku injecteert YTAuto zichzelf in de Google Play Services database zodat Android Auto de app altijd accepteert, ook na updates.

> Shizuku is optioneel. De app werkt volledig zonder Shizuku, maar zonder deze hacks kunnen sommige Android Auto functies beperkt zijn.

---

### Share-to-Car
Deel een link vanuit **elke andere app** direct naar YTAuto:
1. Open een YouTube-video of Spotify-nummer in de bijbehorende app.
2. Tik op **Delen** en kies **YTAuto**.
3. De app zoekt het nummer automatisch op en start het afspelen.

Voor Spotify-links extraheert de app de songtitel uit de webpagina en zoekt het equivalent op YouTube.

---

### Party Mode
Party Mode zet je telefoon om in een mini-webserver. Passagiers in de auto kunnen via hun eigen telefoon — verbonden met hetzelfde Wi-Fi of hotspot — nummers toevoegen aan de wachtrij zonder dat ze de app nodig hebben.

**Hoe het werkt:**
1. Open **Instellingen** in YTAuto.
2. Onder "Party Mode" zie je een URL zoals `http://192.168.1.100:8080`.
3. Kopieer de URL en stuur hem naar je passagiers (WhatsApp, etc.).
4. Passagiers openen de URL in hun browser en plakken een YouTube- of Spotify-link in.
5. Het nummer wordt direct toegevoegd aan de wachtrij in jouw auto.

---

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

---

## Vereisten

- Android **SDK 26+** (target SDK 36 / Android 16)
- Android Studio **Ladybug (2024.2+)** of hoger
- Shizuku-app *(optioneel — voor rij-restrictie hacks)*

---

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

---

## Eerste gebruik

1. Start de app en open **Instellingen**
2. *(Optioneel)* Start Shizuku en druk op **"Injecteer in Android Auto"** voor volledige toegang
3. Verbind je telefoon met Android Auto
4. Zoek een nummer in de app of in Android Auto via de zoekfunctie
5. Gebruik de **video/audio-knop** in de Now Playing balk om te wisselen tussen streams

---

## ⚠️ Belangrijk

YTAuto is een **educatief project** en is niet bedoeld voor distributie via de Google Play Store. De app maakt gebruik van reverse-engineered API's via NewPipe; gebruik is op eigen risico.

---
*Ontwikkeld voor de ultieme roadtrip.*
