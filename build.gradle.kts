import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.compose") version "1.8.0-alpha03"
    kotlin("plugin.serialization") version "2.1.0"
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
val rawVersion: String = project.findProperty("appVersion")?.toString() ?: "4.1.1"
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.16")

    // JNA for Windows API access
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.13")
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
            description = "Daily curated fine art museum wallpapers for your desktop"
            vendor = "Prince"
            copyright = "Copyright © 2026 Prince. All rights reserved."
            licenseFile.set(project.file("LICENSE"))
            appResourcesRootDir.set(project.file("src/main/resources/packaging"))

            // Bundle full modular OpenJDK runtime (includes java.xml for Logback, java.logging, and jdk.crypto.ec for TLS)
            includeAllModules = true

            // Add JDK bundling configuration
            jvmArgs += listOf(
                "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
                "--add-opens", "java.desktop/java.awt.event=ALL-UNNAMED"
            )

            macOS {
                bundleID = appGroup
                iconFile.set(project.file("src/main/resources/icons/icon.icns"))
                packageVersion = appVersion

                val signIdentity = project.findProperty("macSignIdentity")?.toString()
                if (!signIdentity.isNullOrBlank()) {
                    signing {
                        sign.set(true)
                        identity.set(signIdentity)
                    }
                }

                // Standard macOS application presentation and Retina / High-DPI support
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSSupportsAutomaticGraphicsSwitching</key>
                        <true/>
                        <key>NSHighResolutionCapable</key>
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


// Inject custom WiX packaging templates & bitmaps into jpackage for MSI packaging
val copyWixResources by tasks.registering(Copy::class) {
    from(project.file("src/main/resources/packaging")) {
        include("main.wxs", "overrides.wxi", "WixUIBannerBmp.bmp", "WixUIDialogBmp.bmp")
    }
    into(project.layout.buildDirectory.dir("compose/tmp/resources"))
}

tasks.matching { it.name.startsWith("package") && it.name.endsWith("Msi", ignoreCase = true) }.configureEach {
    dependsOn(copyWixResources)
}

tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask>().configureEach {
    if (name.contains("Msi", ignoreCase = true)) {
        val resDir = project.file("src/main/resources/packaging")
        freeArgs.addAll(listOf("--resource-dir", resDir.absolutePath))
        doFirst {
            val composeTmpRes = project.layout.buildDirectory.dir("compose/tmp/resources").get().asFile
            composeTmpRes.mkdirs()
            resDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    file.copyTo(composeTmpRes.resolve(file.name), overwrite = true)
                }
            }
        }
    }
}
