import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "1.9.0"
    id("org.jetbrains.compose") version "1.8.0-alpha03"
    kotlin("plugin.serialization") version "1.9.0"
}

group = "com.example"
version = "3.3.0"

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
    
    // Kotlin Coroutines with Desktop Dispatcher
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
    
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
            packageName = "Art Wallpaper"
            packageVersion = "3.3.0"
            
            // Bundle JDK with the application
            includeAllModules = true
            
            // Add JDK bundling configuration
            jvmArgs += listOf(
                "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
                "--add-opens", "java.desktop/java.awt.event=ALL-UNNAMED"
            )
            
            modules("java.instrument", "java.management", "java.naming", "java.sql")
            
            windows {
                menuGroup = "Art Wallpaper"
                upgradeUuid = "89ABC678-DEF0-1234-5678-ABCDEF123457"
                msiPackageVersion = "3.3.0"
                exePackageVersion = "3.3.0"
                
                // Set application icon
                iconFile.set(project.file("src/main/resources/app_icon.ico"))
                
                dirChooser = true
                perUserInstall = true
                shortcut = true
                
                // Set specific installation path to match WindowsAutoStart
                installationPath = "C:\\Users\\%USERNAME%\\AppData\\Local\\Art Wallpaper"
                
                // Add startup parameters
                jvmArgs += listOf("-Dfile.encoding=UTF-8")
            }
        }
    }
} 