import com.sun.jna.Native
import com.sun.jna.win32.W32APIOptions
import org.slf4j.LoggerFactory
import java.nio.file.Path

interface User32 : com.sun.jna.platform.win32.User32 {
    companion object {
        val INSTANCE = Native.load("user32", User32::class.java, W32APIOptions.DEFAULT_OPTIONS) as User32
    }

    fun SystemParametersInfoW(uiAction: Int, uiParam: Int, pvParam: String, fWinIni: Int): Boolean
}

class WindowsWallpaperService {
    private val logger = LoggerFactory.getLogger(WindowsWallpaperService::class.java)
    
    companion object {
        const val SPI_SETDESKWALLPAPER = 0x0014
        const val SPI_GETDESKWALLPAPER = 0x0073
        const val SPIF_UPDATEINIFILE = 0x01
        const val SPIF_SENDCHANGE = 0x02
    }

    fun setWallpaper(imagePath: Path): Result<Unit> = runCatching {
        logger.info("Setting wallpaper: $imagePath")
        
        val absolutePath = imagePath.toAbsolutePath().toString()
        
        val result = User32.INSTANCE.SystemParametersInfoW(
            SPI_SETDESKWALLPAPER,
            0,
            absolutePath,
            SPIF_UPDATEINIFILE or SPIF_SENDCHANGE
        )

        if (!result) {
            throw RuntimeException("Failed to set wallpaper")
        }
        
        logger.info("Wallpaper set successfully")
    }
}