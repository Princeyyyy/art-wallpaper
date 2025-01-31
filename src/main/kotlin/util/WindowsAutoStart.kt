import com.sun.jna.platform.win32.WinReg
import com.sun.jna.platform.win32.Advapi32Util
import org.slf4j.LoggerFactory

object WindowsAutoStart {
    private val logger = LoggerFactory.getLogger(WindowsAutoStart::class.java)
    private const val REGISTRY_KEY = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val APP_NAME = "ArtWallpaper"

    fun enable() {
        try {
            val path = System.getProperty("user.dir")
            val exePath = "$path\\ArtWallpaper.exe"
            
            Advapi32Util.registrySetStringValue(
                WinReg.HKEY_CURRENT_USER,
                REGISTRY_KEY,
                APP_NAME,
                "$exePath --minimized"
            )
            logger.info("Enabled auto-start for ArtWallpaper")
        } catch (e: Exception) {
            logger.error("Failed to enable auto-start", e)
        }
    }
}