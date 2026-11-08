import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.Library
import java.awt.Window

/**
 * Windows 11 Desktop Window Manager integration.
 *
 * Applies DWMWA_USE_IMMERSIVE_DARK_MODE (attribute 20) to the native HWND
 * of the Compose window so the Win32 title bar, caption buttons, and window
 * border all match the app's dark/light theme — not just the content area.
 *
 * Supported on: Windows 11 and Windows 10 build 19041+.
 * Safe-no-op on: macOS, Linux, older Windows builds (all exceptions swallowed).
 */
object WindowsPlatform {

    private val isWindows = System.getProperty("os.name", "").lowercase().contains("windows")

    /** JNA mapping for dwmapi.dll */
    private interface DwmApi : Library {
        fun DwmSetWindowAttribute(hwnd: HWND, attr: Int, pvAttr: Memory, cbAttr: Int): Int
    }

    private val dwmApi: DwmApi? by lazy {
        if (!isWindows) null
        else try {
            Native.load("dwmapi", DwmApi::class.java) as DwmApi
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Toggles the Win32 immersive dark title bar on the given AWT window.
     * Must be called after the window's peer is created (e.g., inside a LaunchedEffect).
     */
    fun applyDarkTitleBar(window: Window, dark: Boolean) {
        if (!isWindows) return
        val api = dwmApi ?: return
        try {
            val ptr = Native.getComponentPointer(window)
            if (ptr == 0L) return
            val hwnd = HWND(Pointer(ptr))
            val value = Memory(4).also { it.setInt(0, if (dark) 1 else 0) }
            // DWMWA_USE_IMMERSIVE_DARK_MODE = 20
            api.DwmSetWindowAttribute(hwnd, 20, value, 4)
        } catch (_: Exception) {
            // Non-fatal: older Windows builds don't support this attribute
        }
    }

    /**
     * Windows 11 rounded window corners.
     * DWMWA_WINDOW_CORNER_PREFERENCE = 33, value 2 = DWMWCP_ROUND
     */
    fun applyRoundedCorners(window: Window) {
        if (!isWindows) return
        val api = dwmApi ?: return
        try {
            val ptr = Native.getComponentPointer(window)
            if (ptr == 0L) return
            val hwnd = HWND(Pointer(ptr))
            val value = Memory(4).also { it.setInt(0, 2) } // DWMWCP_ROUND
            api.DwmSetWindowAttribute(hwnd, 33, value, 4)
        } catch (_: Exception) {}
    }
}
