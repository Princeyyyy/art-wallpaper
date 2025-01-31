import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "1.9.0"
    id("org.jetbrains.compose") version "1.5.0"
    kotlin("plugin.serialization") version "1.9.0"
}

group = "com.example"
version = "1.0.0"

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material)
    implementation(compose.runtime)
    
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.12")
    
    // JNA for Windows API access
    implementation("net.java.dev.jna:jna:5.13.0")
    implementation("net.java.dev.jna:jna-platform:5.13.0")
}

compose.desktop {
    application {
        mainClass = "MainKt"
        
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ArtWallpaper"
            packageVersion = "1.0.0"
            
            // Bundle JDK with the application
            includeAllModules = true
            
            // Add JDK bundling configuration
            jvmArgs += listOf(
                "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
                "--add-opens", "java.desktop/java.awt.event=ALL-UNNAMED"
            )
            
            modules("java.instrument", "java.management", "java.naming", "java.sql")
            
            windows {
                menuGroup = "ArtWallpaper"
                upgradeUuid = "89ABC678-DEF0-1234-5678-ABCDEF123456"
                msiPackageVersion = "1.0.0"
                exePackageVersion = "1.0.0"
                iconFile.set(project.file("src/main/resources/tray_icon.png"))
                
                dirChooser = true
                perUserInstall = true
                shortcut = true
                menuGroup = "ArtWallpaper"

                // Bundle JDK for Windows
                jvmArgs += listOf("-Dfile.encoding=UTF-8")
            }
        }
    }
} 