pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        gradlePluginPortal()
        mavenCentral()
    }

    plugins {
        kotlin("jvm") version "1.9.0"
        kotlin("plugin.serialization") version "1.9.0"
        id("org.jetbrains.compose") version "1.5.0"
    }
}

rootProject.name = "art-wallpaper"