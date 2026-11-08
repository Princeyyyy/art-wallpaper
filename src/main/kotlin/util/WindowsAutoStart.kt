import com.sun.jna.platform.win32.WinReg
import com.sun.jna.platform.win32.Advapi32Util
import org.slf4j.LoggerFactory
import java.io.File

object WindowsAutoStart {
    private val logger = LoggerFactory.getLogger(WindowsAutoStart::class.java)
    private const val REGISTRY_KEY = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val APP_NAME = "ArtWallpaper"

    fun enable() {
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
                logger.info("Running in development/portable mode (exe exists: ${exeFile.exists()}), saving auto-start state")
                val currentSettings = Settings.currentSettings.value
                if (!currentSettings.startWithSystem || !currentSettings.hasEnabledAutoStart) {
                    currentSettings.copy(
                        startWithSystem = true,
                        hasEnabledAutoStart = true
                    ).save()
                }
                if (!exeFile.exists()) {
                    logger.debug("Executable not yet installed at expected path: $exePath - will register once packaged")
                    return
                }
            }

            // Production mode - register in Windows Run key
            val registryValue = "\"$exePath\" --minimized --autostart"
            logger.info("Setting registry value to: $registryValue")

            Advapi32Util.registrySetStringValue(
                WinReg.HKEY_CURRENT_USER,
                REGISTRY_KEY,
                APP_NAME,
                registryValue
            )

            // Verify registry entry
            if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, REGISTRY_KEY, APP_NAME)) {
                val verifyPath = Advapi32Util.registryGetStringValue(
                    WinReg.HKEY_CURRENT_USER,
                    REGISTRY_KEY,
                    APP_NAME
                )
                logger.info("Verified auto-start registry value: $verifyPath")
            }
        } catch (e: Exception) {
            logger.warn("Could not register auto-start in Windows registry: ${e.message}")
            // Fall back to setting persistence so user setting is preserved
            val currentSettings = Settings.currentSettings.value
            currentSettings.copy(startWithSystem = true, hasEnabledAutoStart = true).save()
        }
    }

    fun disable() {
        try {
            if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, REGISTRY_KEY, APP_NAME)) {
                Advapi32Util.registryDeleteValue(
                    WinReg.HKEY_CURRENT_USER,
                    REGISTRY_KEY,
                    APP_NAME
                )
                logger.info("Disabled auto-start for ArtWallpaper")
            }
        } catch (e: Exception) {
            logger.warn("Could not remove auto-start registry entry: ${e.message}")
        } finally {
            val currentSettings = Settings.currentSettings.value
            if (currentSettings.startWithSystem || currentSettings.hasEnabledAutoStart) {
                currentSettings.copy(
                    startWithSystem = false,
                    hasEnabledAutoStart = false
                ).save()
            }
        }
    }

    fun isEnabled(): Boolean {
        return try {
            val exists = Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, REGISTRY_KEY, APP_NAME)
            if (exists) {
                val value = Advapi32Util.registryGetStringValue(
                    WinReg.HKEY_CURRENT_USER,
                    REGISTRY_KEY,
                    APP_NAME
                )
                logger.debug("Auto-start is enabled with value: $value")
            }
            exists || (Settings.currentSettings.value.startWithSystem && Settings.currentSettings.value.hasEnabledAutoStart)
        } catch (e: Exception) {
            logger.debug("Registry check failed, falling back to settings: ${e.message}")
            Settings.currentSettings.value.startWithSystem
        }
    }
}
