// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ytauto"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ytauto"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // ── AndroidX Media3 (de ENIGE media-bibliotheek die we gebruiken) ──
    val media3Version = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    // DASH/HLS ondersteuning (optioneel, maar handig voor sommige streams)
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")

    // ── Jetpack Compose ──
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // ── Afbeeldingen laden (thumbnails) ──
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ── Coroutines ──
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")

    // ── NewPipe Extractor (YouTube scraper) ──
    // Via JitPack vanuit de officiële TeamNewPipe repository.
    // Controleer https://github.com/AkshayVR/NewPipeExtractor voor de laatste versie.
    implementation("com.github.AkshayVR:NewPipeExtractor:v0.24.2")

    // ── OkHttp (voor de NewPipe Downloader implementatie) ──
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ── AndroidX Core ──
    implementation("androidx.core:core-ktx:1.15.0")

    // ── Guava (voor ListenableFuture in Media3 callbacks) ──
    implementation("com.google.guava:guava:33.3.1-android")
}
