import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "1.9.0"
    id("org.jetbrains.compose") version "1.8.0-alpha03"
    kotlin("plugin.serialization") version "1.9.0"
}

fun normalizeVersion(raw: String): String {
    val clean = raw.trim().removePrefix("v").removePrefix("V")
    val parts = clean.split('.').mapNotNull { part ->
        part.takeWhile { it.isDigit() }.toIntOrNull()
    }
    val major = parts.getOrNull(0) ?: 4
    val minor = parts.getOrNull(1) ?: 0
    val patch = parts.getOrNull(2) ?: 0
    return "$major.$minor.$patch"
}

// Single source of truth for build versioning (from gradle.properties or -PappVersion)
val rawVersion: String = project.findProperty("appVersion")?.toString() ?: "4.0.3"
val appVersion: String = normalizeVersion(rawVersion)
val appGroup: String = project.findProperty("appGroup")?.toString() ?: "com.prince.artwallpaper"

group = appGroup
version = appVersion

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

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("io.mockk:mockk:1.13.10")
}

tasks.test {
    useJUnit()
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "Art Wallpaper"
            packageVersion = appVersion
            appResourcesRootDir.set(project.file("src/main/resources/packaging"))

            // Optimized JRE bundling: only bundle required modules to shrink distribution size
            modules(
                "java.base",
                "java.desktop",
                "java.net.http",
                "java.prefs",
                "java.naming",
                "java.sql",
                "java.instrument",
                "java.management",
                "jdk.unsupported"
            )

            // Add JDK bundling configuration
            jvmArgs += listOf(
                "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
                "--add-opens", "java.desktop/java.awt.event=ALL-UNNAMED"
            )

            macOS {
                bundleID = appGroup
                iconFile.set(project.file("src/main/resources/icons/icon.icns"))
                packageVersion = appVersion

                // Hide app from macOS Dock and Cmd+Tab — it lives in the menu bar only.
                // LSUIElement=1 is the standard Info.plist key for status-bar / helper apps
                // (like Bartender, Lungo, Hand Mirror, etc.).
                infoPlist {
                    extraKeysRawXml = """
                        <key>LSUIElement</key>
                        <true/>
                        <key>NSSupportsAutomaticGraphicsSwitching</key>
                        <true/>
                    """.trimIndent()
                }
            }

            windows {
                menu = true
                menuGroup = "Art Wallpaper"
                upgradeUuid = "89ABC678-DEF0-1234-5678-ABCDEF123457"
                msiPackageVersion = appVersion
                exePackageVersion = appVersion

                // Set application icon
                iconFile.set(project.file("src/main/resources/icons/icon.ico"))

                dirChooser = true
                perUserInstall = true
                shortcut = true


                // Add startup parameters
                jvmArgs += listOf("-Dfile.encoding=UTF-8")
            }
        }
    }
}
