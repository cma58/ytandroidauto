// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack is vereist voor NewPipe Extractor
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "YTAuto"
include(":app")
