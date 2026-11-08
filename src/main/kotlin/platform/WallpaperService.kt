import com.sun.jna.Native
import com.sun.jna.win32.W32APIOptions
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Common cross-platform interface for desktop background wallpaper control.
 */
interface WallpaperService {
    fun setWallpaper(imagePath: Path): Result<Unit>

    companion object {
        fun create(): WallpaperService {
            val os = System.getProperty("os.name", "").lowercase()
            return when {
                os.contains("mac") -> MacWallpaperService()
                else -> WindowsWallpaperService()
            }
        }
    }
}

// ── Windows Implementation (Win32 API via JNA) ───────────────────────────────

interface User32 : com.sun.jna.platform.win32.User32 {
    companion object {
        val INSTANCE: User32 by lazy {
            Native.load("user32", User32::class.java, W32APIOptions.DEFAULT_OPTIONS) as User32
        }
    }

    fun SystemParametersInfoW(uiAction: Int, uiParam: Int, pvParam: String, fWinIni: Int): Boolean
}

class WindowsWallpaperService : WallpaperService {
    private val logger = LoggerFactory.getLogger(WindowsWallpaperService::class.java)

    companion object {
        const val SPI_SETDESKWALLPAPER = 0x0014
        const val SPIF_UPDATEINIFILE = 0x01
        const val SPIF_SENDCHANGE = 0x02
    }

    override fun setWallpaper(imagePath: Path): Result<Unit> = runCatching {
        logger.info("Setting Windows desktop wallpaper: $imagePath")

        val absolutePath = imagePath.toAbsolutePath().toString()

        val result = User32.INSTANCE.SystemParametersInfoW(
            SPI_SETDESKWALLPAPER,
            0,
            absolutePath,
            SPIF_UPDATEINIFILE or SPIF_SENDCHANGE
        )

        if (!result) {
            throw RuntimeException("Win32 SystemParametersInfoW failed to set wallpaper")
        }

        logger.info("Windows wallpaper set successfully")
    }
}

// ── macOS Implementation (Native AppleScript / System Events) ────────────────

class MacWallpaperService : WallpaperService {
    private val logger = LoggerFactory.getLogger(MacWallpaperService::class.java)

    override fun setWallpaper(imagePath: Path): Result<Unit> = runCatching {
        val posixPath = imagePath.toAbsolutePath().toString()
        logger.info("Setting macOS desktop wallpaper: $posixPath")

        // Sets the wallpaper across all active Spaces and connected displays
        val script = "tell application \"System Events\" to tell every desktop to set picture to \"$posixPath\""
        val process = ProcessBuilder("osascript", "-e", script)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException("osascript failed (code $exitCode): $output")
        }

        logger.info("macOS wallpaper set successfully")
    }
}
