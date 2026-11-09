import androidx.compose.runtime.*
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import kotlinx.coroutines.delay

/**
 * Cross-platform OS system-theme detection.
 *
 * Windows 11: reads AppsUseLightTheme from the Personalize registry key via JNA.
 * macOS:      queries the global AppleInterfaceStyle default via `defaults read`.
 * Other:      defaults to dark (safe fallback for a gallery/art app).
 */
object SystemThemeDetector {

    private val isWindows = System.getProperty("os.name", "").lowercase().contains("windows")
    private val isMac     = System.getProperty("os.name", "").lowercase().contains("mac")

    fun isDark(): Boolean = when {
        isWindows -> windowsIsDark()
        isMac     -> macIsDark()
        else      -> true
    }

    private fun windowsIsDark(): Boolean = try {
        val lightTheme = Advapi32Util.registryGetIntValue(
            WinReg.HKEY_CURRENT_USER,
            "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "AppsUseLightTheme"
        )
        lightTheme == 0  // 0 = Dark mode active
    } catch (_: Exception) {
        true  // Fallback: assume dark if registry unreadable
    }

    private fun macIsDark(): Boolean = try {
        val process = ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
            .redirectErrorStream(true)
            .start()
        process.waitFor()
        process.inputStream.bufferedReader().readText().trim().lowercase() == "dark"
    } catch (_: Exception) {
        true  // Key absent = Light mode on macOS, but we default dark for art
    }
}

/**
 * Composable that tracks OS theme changes live.
 * Polls every 5 seconds — sufficient responsiveness without busy-looping.
 */
@Composable
fun rememberSystemIsDark(): Boolean {
    var isDark by remember { mutableStateOf(SystemThemeDetector.isDark()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000L)
            val current = SystemThemeDetector.isDark()
            if (current != isDark) isDark = current
        }
    }
    return isDark
}
