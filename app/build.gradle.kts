// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "com.ytauto"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ytauto"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE")?.toIntOrNull()) ?: 1
        versionName = System.getenv("VERSION_CODE") ?: "1"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
            if (!keystorePath.isNullOrEmpty()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Use consistent signing key when available so OTA updates install cleanly
            val cfg = signingConfigs.getByName("release")
            if (cfg.storeFile?.exists() == true) signingConfig = cfg
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val cfg = signingConfigs.getByName("release")
            if (cfg.storeFile?.exists() == true) signingConfig = cfg
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
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
    // Gebruik de officiële TeamNewPipe repository voor stabiliteit.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.1")

    // ── OkHttp (voor de NewPipe Downloader implementatie) ──
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ── AndroidX Core ──
    implementation("androidx.core:core-ktx:1.15.0")

    // ── DataStore (persistente instellingen) ──
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ── Android Car App Library (voor de Video-Hack) ──
    // app-automotive is ALLEEN voor Automotive OS. Voor telefoon + Android Auto projectie:
    val carAppVersion = "1.7.0"
    implementation("androidx.car.app:app:$carAppVersion")
    implementation("androidx.car.app:app-projected:$carAppVersion")

    // ── Palette API ──
    implementation("androidx.palette:palette-ktx:1.0.0")

    // ── WorkManager (voor Offline Sync) ──
    val workVersion = "2.10.0"
    implementation("androidx.work:work-runtime-ktx:$workVersion")

    // ── Room (Database voor Offline Tracks) ──
    val roomVersion = "2.7.0-alpha01"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ── Guava (voor ListenableFuture in Media3 callbacks) ──
    implementation("com.google.guava:guava:33.3.1-android")

    // ── Ktor (voor de Gast Casting Webserver) ──
    val ktorVersion = "2.3.12"
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder:$ktorVersion")

    // ── Shizuku (voor diepe systeemtoegang zonder root) ──
    val shizukuVersion = "13.1.5"
    implementation("dev.rikka.shizuku:api:$shizukuVersion")
    implementation("dev.rikka.shizuku:provider:$shizukuVersion")
}
