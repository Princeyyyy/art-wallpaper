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

// ── macOS Implementation (Cocoa NSWorkspace + AppleScript Fallback) ─────────

class MacWallpaperService : WallpaperService {
    private val logger = LoggerFactory.getLogger(MacWallpaperService::class.java)

    override fun setWallpaper(imagePath: Path): Result<Unit> = runCatching {
        val posixPath = imagePath.toAbsolutePath().toString()
        logger.info("Setting macOS desktop wallpaper: $posixPath")

        // Primary: AppKit Cocoa NSWorkspace (Zero TCC permissions needed, instantaneous)
        val cocoaSuccess = setWallpaperViaCocoa(posixPath)
        if (cocoaSuccess) {
            logger.info("macOS wallpaper set successfully via Cocoa NSWorkspace")
            return@runCatching
        }

        // Secondary Fallback: AppleScript / System Events
        logger.info("Falling back to AppleScript for macOS wallpaper setting")
        val script = "tell application \"System Events\" to tell every desktop to set picture to \"$posixPath\""
        val process = ProcessBuilder("osascript", "-e", script)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException("osascript failed (code $exitCode): $output")
        }

        logger.info("macOS wallpaper set successfully via AppleScript")
    }

    private fun setWallpaperViaCocoa(posixPath: String): Boolean {
        val lib = MacPlatform.objc ?: return false
        return try {
            val nsWorkspaceClass = lib.objc_getClass("NSWorkspace")
            val sharedWorkspaceSel = lib.sel_registerName("sharedWorkspace")
            val workspace = lib.objc_msgSend(nsWorkspaceClass, sharedWorkspaceSel)
            if (workspace == 0L) return false

            val nsStringClass = lib.objc_getClass("NSString")
            val strWithUtf8Sel = lib.sel_registerName("stringWithUTF8String:")
            val pathNs = lib.objc_msgSend(nsStringClass, strWithUtf8Sel, posixPath)
            if (pathNs == 0L) return false

            val nsUrlClass = lib.objc_getClass("NSURL")
            val fileURLWithPathSel = lib.sel_registerName("fileURLWithPath:")
            val urlNs = lib.objc_msgSend(nsUrlClass, fileURLWithPathSel, pathNs)
            if (urlNs == 0L) return false

            val nsScreenClass = lib.objc_getClass("NSScreen")
            val screensSel = lib.sel_registerName("screens")
            val screens = lib.objc_msgSend(nsScreenClass, screensSel)

            val countSel = lib.sel_registerName("count")
            val objectAtIndexSel = lib.sel_registerName("objectAtIndex:")
            val count = lib.objc_msgSend(screens, countSel)

            val setDesktopSel = lib.sel_registerName("setDesktopImageURL:forScreen:options:error:")

            var anySuccess = false
            for (i in 0 until count) {
                val screen = lib.objc_msgSend(screens, objectAtIndexSel, i)
                val ok = lib.objc_msgSend(workspace, setDesktopSel, urlNs, screen, 0L, 0L)
                if (ok != 0L) anySuccess = true
            }
            anySuccess
        } catch (e: Exception) {
            logger.debug("Cocoa wallpaper setting failed: ${e.message}")
            false
        }
    }
}
