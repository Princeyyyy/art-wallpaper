import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Cross-platform desktop startup integration service.
 */
interface AutoStartService {
    fun enable()
    fun disable()
    fun isEnabled(): Boolean
    /** Refreshes existing auto-start registration when format upgrades are needed. */
    fun refreshIfNeeded() {}

    companion object {
        fun create(): AutoStartService {
            val os = System.getProperty("os.name", "").lowercase()
            return when {
                os.contains("mac") -> MacAutoStartService()
                else -> WindowsAutoStartService()
            }
        }
    }
}

// ── Windows Auto-Start (Run Key via JNA) ──────────────────────────────────────

class WindowsAutoStartService : AutoStartService {
    private val logger = LoggerFactory.getLogger(WindowsAutoStartService::class.java)

    companion object {
        private const val REGISTRY_KEY = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run"
        private const val APP_NAME = "ArtWallpaper"
    }

    override fun enable() {
        try {
            val classPath = System.getProperty("java.class.path") ?: ""
            val isDevelopment = classPath.contains("build\\classes") ||
                    classPath.contains("build/classes") ||
                    classPath.contains(".gradle") ||
                    System.getProperty("idea.active") == "true"

            val localAppData = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
            val exePath = "$localAppData\\Art Wallpaper\\Art Wallpaper.exe"
            val exeFile = File(exePath)

            if (isDevelopment || !exeFile.exists()) {
                logger.info("Running in development/portable mode, updating settings")
                val currentSettings = Settings.currentSettings.value
                if (!currentSettings.startWithSystem || !currentSettings.hasEnabledAutoStart) {
                    runCatching { currentSettings.copy(startWithSystem = true, hasEnabledAutoStart = true).save() }
                }
                if (!exeFile.exists()) return
            }

            val registryValue = "\"$exePath\" --minimized --autostart"
            Advapi32Util.registrySetStringValue(
                WinReg.HKEY_CURRENT_USER,
                REGISTRY_KEY,
                APP_NAME,
                registryValue
            )
            logger.info("Windows auto-start registered: $registryValue")
        } catch (e: Exception) {
            logger.warn("Could not register Windows auto-start: ${e.message}")
            val currentSettings = Settings.currentSettings.value
            runCatching { currentSettings.copy(startWithSystem = true, hasEnabledAutoStart = true).save() }
        }
    }

    override fun disable() {
        try {
            if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, REGISTRY_KEY, APP_NAME)) {
                Advapi32Util.registryDeleteValue(
                    WinReg.HKEY_CURRENT_USER,
                    REGISTRY_KEY,
                    APP_NAME
                )
                logger.info("Windows auto-start unregistered")
            }
        } catch (e: Exception) {
            logger.warn("Could not remove Windows auto-start: ${e.message}")
        } finally {
            val currentSettings = Settings.currentSettings.value
            if (currentSettings.startWithSystem || currentSettings.hasEnabledAutoStart) {
                runCatching { currentSettings.copy(startWithSystem = false, hasEnabledAutoStart = false).save() }
            }
        }
    }

    override fun isEnabled(): Boolean {
        return try {
            val exists = Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, REGISTRY_KEY, APP_NAME)
            exists || (Settings.currentSettings.value.startWithSystem && Settings.currentSettings.value.hasEnabledAutoStart)
        } catch (e: Exception) {
            Settings.currentSettings.value.startWithSystem
        }
    }

    override fun refreshIfNeeded() {
        try {
            if (!Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, REGISTRY_KEY, APP_NAME)) return
            val existing = Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, REGISTRY_KEY, APP_NAME)
            val localAppData = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
            val exePath = "$localAppData\\Art Wallpaper\\Art Wallpaper.exe"
            val exeFile = File(exePath)
            if (exeFile.exists()) {
                val expected = "\"$exePath\" --minimized --autostart"
                if (existing != expected) {
                    Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, REGISTRY_KEY, APP_NAME, expected)
                    logger.info("Refreshed Windows auto-start registry value: $expected")
                }
            }
        } catch (e: Exception) {
            logger.debug("Could not refresh Windows auto-start registry entry: ${e.message}")
        }
    }
}

// ── macOS Auto-Start (LaunchAgents plist) ─────────────────────────────────────

class MacAutoStartService(
    private val launchAgentsDir: Path = defaultLaunchAgentsDir(),
    private val bundleId: String = AppInfo.BUNDLE_ID,
    appExecutablePath: String? = null,
    private val persistSettings: Boolean = (launchAgentsDir == defaultLaunchAgentsDir())
) : AutoStartService {
    private val logger = LoggerFactory.getLogger(MacAutoStartService::class.java)
    private val plistFile: Path = launchAgentsDir.resolve("$bundleId.plist")
    val appExecutablePath: String = appExecutablePath ?: resolveRunningExecutablePath()

    companion object {
        fun defaultLaunchAgentsDir(): Path = Path.of(System.getProperty("user.home"), "Library", "LaunchAgents")

        fun resolveRunningExecutablePath(): String {
            val cmd = ProcessHandle.current().info().command().orElse("")
            if (cmd.contains(".app/Contents/MacOS/")) {
                return cmd
            }
            return "/Applications/Art Wallpaper.app/Contents/MacOS/Art Wallpaper"
        }

        fun resolveAppBundlePath(executableOrBundlePath: String = resolveRunningExecutablePath()): String {
            val marker = ".app/Contents/MacOS/"
            val idx = executableOrBundlePath.indexOf(marker)
            if (idx >= 0) {
                return executableOrBundlePath.substring(0, idx + 4)
            }
            if (executableOrBundlePath.endsWith(".app")) {
                return executableOrBundlePath
            }
            return "/Applications/Art Wallpaper.app"
        }

        private fun escapeXml(value: String): String =
            value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }

    override fun enable() {
        try {
            launchAgentsDir.createDirectories()
            plistFile.writeText(generatePlist())
            logger.info("Created macOS LaunchAgent at $plistFile")

            if (persistSettings) {
                val currentSettings = Settings.currentSettings.value
                if (!currentSettings.startWithSystem || !currentSettings.hasEnabledAutoStart) {
                    runCatching { currentSettings.copy(startWithSystem = true, hasEnabledAutoStart = true).save() }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to enable auto-start on macOS", e)
        }
    }

    override fun disable() {
        try {
            plistFile.deleteIfExists()
            logger.info("Removed macOS LaunchAgent at $plistFile")
        } catch (e: Exception) {
            logger.error("Failed to disable auto-start on macOS", e)
        } finally {
            if (persistSettings) {
                val currentSettings = Settings.currentSettings.value
                if (currentSettings.startWithSystem || currentSettings.hasEnabledAutoStart) {
                    runCatching { currentSettings.copy(startWithSystem = false, hasEnabledAutoStart = false).save() }
                }
            }
        }
    }

    override fun isEnabled(): Boolean = plistFile.exists()

    override fun refreshIfNeeded() {
        if (!plistFile.exists()) return
        try {
            val existing = plistFile.readText()
            val expected = generatePlist()
            if (!existing.contains("<string>/usr/bin/open</string>") || existing != expected) {
                plistFile.writeText(expected)
                logger.info("Updated macOS LaunchAgent plist to LaunchServices launcher at $plistFile")
            }
        } catch (e: Exception) {
            logger.warn("Failed to refresh macOS LaunchAgent plist", e)
        }
    }

    fun generatePlist(): String {
        val safeBundleId = escapeXml(bundleId)
        val safeBundlePath = escapeXml(resolveAppBundlePath(appExecutablePath))
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>Label</key>
                <string>$safeBundleId</string>
                <key>AssociatedBundleIdentifiers</key>
                <string>$safeBundleId</string>
                <key>ProgramArguments</key>
                <array>
                    <string>/usr/bin/open</string>
                    <string>-g</string>
                    <string>-a</string>
                    <string>$safeBundlePath</string>
                    <string>--args</string>
                    <string>--minimized</string>
                    <string>--autostart</string>
                </array>
                <key>RunAtLoad</key>
                <true/>
                <key>KeepAlive</key>
                <false/>
            </dict>
            </plist>
        """.trimIndent()
    }
}
