# ProGuard rules voor YTAuto
# ──────────────────────────────────────

# NewPipe Extractor gebruikt reflectie
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil
-dontwarn coil.**
