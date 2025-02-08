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
            // Get the installation path from environment
            val localAppData = System.getenv("LOCALAPPDATA")
            val exePath = "$localAppData\\ArtWallpaper\\ArtWallpaper.exe"
            val exeFile = File(exePath)

            logger.info("Checking executable at: $exePath")
            
            if (!exeFile.exists()) {
                logger.error("Executable not found at expected path: $exePath")
                throw RuntimeException("Executable not found at expected path")
            }

            val registryValue = "\"$exePath\" --minimized"
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
                logger.info("Successfully disabled auto-start for ArtWallpaper")
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