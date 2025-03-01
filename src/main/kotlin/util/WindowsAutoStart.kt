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
            // Check if we're running in development mode by checking the execution path
            val isDevelopment = System.getProperty("java.class.path")?.contains("build\\classes") == true
            
            if (isDevelopment) {
                logger.info("Running in development mode, skipping auto-start configuration")
                // Update settings to simulate auto-start being enabled
                val currentSettings = Settings.currentSettings.value
                if (!currentSettings.startWithSystem || !currentSettings.hasEnabledAutoStart) {
                    currentSettings.copy(
                        startWithSystem = true,
                        hasEnabledAutoStart = true
                    ).save()
                }
                return
            }

            // Production mode - check for installed executable
            val localAppData = System.getenv("LOCALAPPDATA")
            val exePath = "$localAppData\\Art Wallpaper\\Art Wallpaper.exe"
            val exeFile = File(exePath)

            logger.info("Checking executable at: $exePath")
            
            if (!exeFile.exists()) {
                logger.error("Executable not found at expected path: $exePath")
                throw RuntimeException("Executable not found at expected path")
            }

            // Add --minimized and --autostart flags to ensure proper startup behavior
            val registryValue = "\"$exePath\" --minimized --autostart"
            logger.info("Setting registry value to: $registryValue")

            Advapi32Util.registrySetStringValue(
                WinReg.HKEY_CURRENT_USER,
                REGISTRY_KEY,
                APP_NAME,
                registryValue
            )

            // Verify the registry entry
            if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, REGISTRY_KEY, APP_NAME)) {
                val verifyPath = Advapi32Util.registryGetStringValue(
                    WinReg.HKEY_CURRENT_USER,
                    REGISTRY_KEY,
                    APP_NAME
                )
                logger.info("Verified registry value: $verifyPath")
            } else {
                logger.error("Failed to verify registry entry after setting")
                throw RuntimeException("Registry entry verification failed")
            }

        } catch (e: Exception) {
            logger.error("Failed to enable auto-start", e)
            throw e
        }
    }

    fun disable() {
        try {
            if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, REGISTRY_KEY, APP_NAME)) {
                val currentValue = Advapi32Util.registryGetStringValue(
                    WinReg.HKEY_CURRENT_USER,
                    REGISTRY_KEY,
                    APP_NAME
                )
                logger.info("Removing registry value: $currentValue")
                
                Advapi32Util.registryDeleteValue(
                    WinReg.HKEY_CURRENT_USER,
                    REGISTRY_KEY,
                    APP_NAME
                )
                
                // Update settings to reflect the change
                val currentSettings = Settings.currentSettings.value
                if (currentSettings.startWithSystem || currentSettings.hasEnabledAutoStart) {
                    currentSettings.copy(
                        startWithSystem = false,
                        hasEnabledAutoStart = false
                    ).save()
                }
                
                logger.info("Disabled auto-start for ArtWallpaper")
            } else {
                logger.info("Auto-start was not enabled, nothing to disable")
            }
        } catch (e: Exception) {
            logger.error("Failed to disable auto-start", e)
            throw e
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
                logger.info("Auto-start is enabled with value: $value")
            } else {
                logger.info("Auto-start is not enabled")
            }
            exists
        } catch (e: Exception) {
            logger.error("Failed to check auto-start status", e)
            false
        }
    }
}