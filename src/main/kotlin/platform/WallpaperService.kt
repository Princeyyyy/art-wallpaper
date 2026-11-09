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

        // Set registry wallpaper style to Fill (10) and Tile to 0 to prevent Windows from stretching or tiling
        try {
            com.sun.jna.platform.win32.Advapi32Util.registrySetStringValue(
                com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER,
                "Control Panel\\Desktop",
                "WallpaperStyle",
                "10"
            )
            com.sun.jna.platform.win32.Advapi32Util.registrySetStringValue(
                com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER,
                "Control Panel\\Desktop",
                "TileWallpaper",
                "0"
            )
        } catch (e: Exception) {
            logger.warn("Could not set WallpaperStyle in Windows registry: ${e.message}")
        }

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

// ── macOS Implementation (Native AppleScript / System Events & Finder) ────────

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
            logger.warn("osascript System Events returned code $exitCode: $output. Attempting Finder fallback...")
            // Fallback for Finder / legacy spaces
            val finderScript = "tell application \"Finder\" to set desktop picture to POSIX file \"$posixPath\""
            val fallbackProcess = ProcessBuilder("osascript", "-e", finderScript)
                .redirectErrorStream(true)
                .start()
            val fallbackOutput = fallbackProcess.inputStream.bufferedReader().readText()
            val fallbackExit = fallbackProcess.waitFor()
            if (fallbackExit != 0) {
                throw RuntimeException("macOS wallpaper setting failed: $output | $fallbackOutput")
            }
        }

        logger.info("macOS wallpaper set successfully")
    }
}
