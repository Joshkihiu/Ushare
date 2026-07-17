pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        kotlin("multiplatform") version "2.0.20"
        kotlin("android") version "2.0.20"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"
        id("org.jetbrains.compose") version "1.7.0"
        id("com.android.application") version "8.5.2"
        id("com.android.library") version "8.5.2"
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "UShare"
include(":androidApp")
