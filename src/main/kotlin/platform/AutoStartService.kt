import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * Cross-platform desktop startup integration service.
 */
interface AutoStartService {
    fun enable()
    fun disable()
    fun isEnabled(): Boolean

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
                    currentSettings.copy(startWithSystem = true, hasEnabledAutoStart = true).save()
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
            currentSettings.copy(startWithSystem = true, hasEnabledAutoStart = true).save()
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
                currentSettings.copy(startWithSystem = false, hasEnabledAutoStart = false).save()
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
}

// ── macOS Auto-Start (LaunchAgents plist) ─────────────────────────────────────

class MacAutoStartService(
    private val launchAgentsDir: Path = Path.of(System.getProperty("user.home"), "Library", "LaunchAgents"),
    private val bundleId: String = "com.example.artwallpaper",
    private val appExecutablePath: String = "/Applications/Art Wallpaper.app/Contents/MacOS/Art Wallpaper"
) : AutoStartService {
    private val logger = LoggerFactory.getLogger(MacAutoStartService::class.java)
    private val plistFile: Path = launchAgentsDir.resolve("$bundleId.plist")

    override fun enable() {
        try {
            launchAgentsDir.createDirectories()
            plistFile.writeText(generatePlist())
            logger.info("Created macOS LaunchAgent at $plistFile")

            val currentSettings = Settings.currentSettings.value
            if (!currentSettings.startWithSystem || !currentSettings.hasEnabledAutoStart) {
                currentSettings.copy(startWithSystem = true, hasEnabledAutoStart = true).save()
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
            val currentSettings = Settings.currentSettings.value
            if (currentSettings.startWithSystem || currentSettings.hasEnabledAutoStart) {
                currentSettings.copy(startWithSystem = false, hasEnabledAutoStart = false).save()
            }
        }
    }

    override fun isEnabled(): Boolean = plistFile.exists()

    fun generatePlist(): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>Label</key>
                <string>$bundleId</string>
                <key>ProgramArguments</key>
                <array>
                    <string>$appExecutablePath</string>
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
