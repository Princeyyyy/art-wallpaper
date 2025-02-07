import com.sun.jna.platform.win32.WinReg
import com.sun.jna.platform.win32.Advapi32Util
import org.slf4j.LoggerFactory

object WindowsAutoStart {
    private val logger = LoggerFactory.getLogger(WindowsAutoStart::class.java)
    private const val REGISTRY_KEY = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val APP_NAME = "ArtWallpaper"

    fun enable() {
        try {
            val location = WindowsAutoStart::class.java.protectionDomain.codeSource.location
            val jarPath = location.toURI().path.removePrefix("/")  // Remove leading slash for Windows paths
            val exePath = if (jarPath.endsWith(".jar")) {
                // Running from JAR - use full path to javaw.exe
                val javaHome = System.getProperty("java.home")
                "\"${javaHome}\\bin\\javaw.exe\" -jar \"${jarPath}\" --minimized"
            } else {
                // Running from IDE or exe - use installed exe path
                val installPath = System.getenv("LOCALAPPDATA") + "\\Programs\\ArtWallpaper"
                "\"$installPath\\ArtWallpaper.exe\" --minimized"
            }
            
            Advapi32Util.registrySetStringValue(
                WinReg.HKEY_CURRENT_USER,
                REGISTRY_KEY,
                APP_NAME,
                exePath
            )
            logger.info("Enabled auto-start for ArtWallpaper with path: $exePath")
        } catch (e: Exception) {
            logger.error("Failed to enable auto-start", e)
            throw e
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
            logger.error("Failed to disable auto-start", e)
            throw e
        }
    }

    fun isEnabled(): Boolean {
        return try {
            Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, REGISTRY_KEY, APP_NAME)
        } catch (e: Exception) {
            logger.error("Failed to check auto-start status", e)
            false
        }
    }
}