import kotlin.io.path.exists
import com.sun.jna.Native
import com.sun.jna.win32.W32APIOptions
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Common cross-platform interface for desktop background wallpaper control.
 */
interface WallpaperService {
    fun setWallpaper(imagePath: Path): Result<Unit>
    fun setLockScreenWallpaper(imagePath: Path): Result<Unit> = Result.success(Unit)
    fun getCurrentWallpaper(): Path? = null

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

        // Set registry wallpaper style to Fill (10), Tile to 0, and JPEGImportQuality to 100
        // (prevents Windows 10/11 from recompressing desktop wallpapers at 85% JPEG quality)
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
            com.sun.jna.platform.win32.Advapi32Util.registrySetIntValue(
                com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER,
                "Control Panel\\Desktop",
                "JPEGImportQuality",
                100
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

    override fun setLockScreenWallpaper(imagePath: Path): Result<Unit> = runCatching {
        val absolutePath = imagePath.toAbsolutePath().toString()
        require(imagePath.exists()) { "Lock screen image file does not exist: $absolutePath" }
        logger.info("Setting Windows lock screen wallpaper: $absolutePath")

        val script = """
            ${'$'}ErrorActionPreference = 'Stop'
            ${'$'}imgPath = ${'$'}env:ART_LOCK_IMAGE_PATH
            if (-not (Test-Path -LiteralPath ${'$'}imgPath)) {
                throw "Lock screen image file not found: ${'$'}imgPath"
            }

            # 1. Disable Windows Spotlight lock screen override so custom picture mode is active
            try {
                ${'$'}cdm = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\ContentDeliveryManager'
                if (Test-Path ${'$'}cdm) {
                    Set-ItemProperty -Path ${'$'}cdm -Name 'RotatingLockScreenEnabled' -Value 0 -Type DWord -ErrorAction SilentlyContinue
                    Set-ItemProperty -Path ${'$'}cdm -Name 'RotatingLockScreenOverlayEnabled' -Value 0 -Type DWord -ErrorAction SilentlyContinue
                    Set-ItemProperty -Path ${'$'}cdm -Name 'SubscribedContent-338387Enabled' -Value 0 -Type DWord -ErrorAction SilentlyContinue
                }
            } catch {}

            # 2. Load WinRT interop extensions so COM IAsyncAction / IRandomAccessStream are properly awaited
            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            [Windows.System.UserProfile.LockScreen, Windows.System.UserProfile, ContentType = WindowsRuntime] | Out-Null
            [Windows.Storage.Streams.IRandomAccessStream, Windows.Storage.Streams, ContentType = WindowsRuntime] | Out-Null

            ${'$'}asTaskAction = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
                ${'$'}_.Name -eq 'AsTask' -and ${'$'}_.GetParameters().Count -eq 1 -and ${'$'}_.GetParameters()[0].ParameterType.Name -eq 'IAsyncAction'
            })[0]

            if (${'$'}null -eq ${'$'}asTaskAction) {
                throw "Could not resolve System.WindowsRuntimeSystemExtensions.AsTask(IAsyncAction)"
            }

            # 3. Stream file directly via SetImageStreamAsync to bypass WinRT StorageFile broker path blocks
            ${'$'}fs = [System.IO.File]::OpenRead(${'$'}imgPath)
            try {
                ${'$'}ras = [System.IO.WindowsRuntimeStreamExtensions]::AsRandomAccessStream(${'$'}fs)
                try {
                    ${'$'}asyncAction = [Windows.System.UserProfile.LockScreen]::SetImageStreamAsync(${'$'}ras)
                    ${'$'}netTask = ${'$'}asTaskAction.Invoke(${'$'}null, @(${'$'}asyncAction))
                    ${'$'}completed = ${'$'}netTask.Wait(15000)
                    if (-not ${'$'}completed) {
                        throw "SetImageStreamAsync timed out"
                    }
                    if (${'$'}netTask.IsFaulted -and ${'$'}null -ne ${'$'}netTask.Exception) {
                        throw ${'$'}netTask.Exception.InnerException
                    }
                } finally {
                    if (${'$'}null -ne ${'$'}ras) { ${'$'}ras.Dispose() }
                }
            } finally {
                ${'$'}fs.Dispose()
            }

            # 4. Also update PersonalizationCSP if running with sufficient privileges (non-fatal)
            try {
                ${'$'}csp = 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\PersonalizationCSP'
                if (Test-Path ${'$'}csp) {
                    Set-ItemProperty -Path ${'$'}csp -Name 'LockScreenImagePath' -Value ${'$'}imgPath -Type String -ErrorAction SilentlyContinue
                    Set-ItemProperty -Path ${'$'}csp -Name 'LockScreenImageUrl' -Value ${'$'}imgPath -Type String -ErrorAction SilentlyContinue
                    Set-ItemProperty -Path ${'$'}csp -Name 'LockScreenImageStatus' -Value 1 -Type DWord -ErrorAction SilentlyContinue
                }
            } catch {}
        """.trimIndent()

        val pb = ProcessBuilder(
            "powershell.exe",
            "-NoProfile",
            "-NonInteractive",
            "-WindowStyle", "Hidden",
            "-ExecutionPolicy", "Bypass",
            "-Command", script
        )
        pb.environment()["ART_LOCK_IMAGE_PATH"] = absolutePath
        pb.redirectErrorStream(true)

        val process = pb.start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val finished = process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw RuntimeException("Timed out setting Windows lock screen wallpaper")
        }
        val exitCode = process.exitValue()
        if (exitCode != 0) {
            throw RuntimeException("Windows lock screen update failed (exit $exitCode): $output")
        }
        logger.info("Windows lock screen wallpaper set successfully")
    }

    override fun getCurrentWallpaper(): Path? {
        return try {
            val regPath = com.sun.jna.platform.win32.Advapi32Util.registryGetStringValue(
                com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER,
                "Control Panel\\Desktop",
                "Wallpaper"
            )
            if (!regPath.isNullOrBlank()) {
                val p = Path.of(regPath)
                if (p.exists()) p else null
            } else null
        } catch (e: Exception) {
            logger.warn("Could not read Windows wallpaper registry: ${e.message}")
            null
        }
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

    override fun getCurrentWallpaper(): Path? {
        return try {
            val script = "tell application \"System Events\" to get picture of current desktop"
            val process = ProcessBuilder("osascript", "-e", script)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() == 0 && output.isNotBlank()) {
                val p = Path.of(output)
                if (p.exists()) p else null
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
