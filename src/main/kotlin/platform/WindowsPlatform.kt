import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.Library
import org.slf4j.LoggerFactory
import java.awt.Window

/**
 * Windows Desktop Window Manager and Shell integration.
 *
 * 1. DWMWA_USE_IMMERSIVE_DARK_MODE (attribute 20) for dark caption bar.
 * 2. DWMWA_WINDOW_CORNER_PREFERENCE (attribute 33) for rounded corners.
 * 3. SetCurrentProcessExplicitAppUserModelID for branded taskbar grouping & Action Center notifications.
 * 4. SetForegroundWindow for bringing window to front when reopened.
 */
object WindowsPlatform {

    private val logger = LoggerFactory.getLogger(WindowsPlatform::class.java)
    private val isWindows = System.getProperty("os.name", "").lowercase().contains("windows")

    /** JNA mapping for dwmapi.dll */
    private interface DwmApi : Library {
        fun DwmSetWindowAttribute(hwnd: HWND, attr: Int, pvAttr: Memory, cbAttr: Int): Int
    }

    /** JNA mapping for shell32.dll */
    private interface Shell32 : Library {
        fun SetCurrentProcessExplicitAppUserModelID(appID: WString): Int
    }

    /** JNA mapping for user32.dll */
    private interface User32Helper : Library {
        fun SetForegroundWindow(hwnd: HWND): Boolean
        fun ShowWindow(hwnd: HWND, nCmdShow: Int): Boolean
    }

    private val dwmApi: DwmApi? by lazy {
        if (!isWindows) null
        else try {
            Native.load("dwmapi", DwmApi::class.java) as DwmApi
        } catch (_: Exception) {
            null
        }
    }

    private val shell32: Shell32? by lazy {
        if (!isWindows) null
        else try {
            Native.load("shell32", Shell32::class.java) as Shell32
        } catch (_: Exception) {
            null
        }
    }

    private val user32: User32Helper? by lazy {
        if (!isWindows) null
        else try {
            Native.load("user32", User32Helper::class.java) as User32Helper
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Registers the application's explicit AppUserModelID with the Windows Shell.
     * Required for proper Start Menu shortcut association, taskbar grouping,
     * and Action Center branded icon display in notifications.
     */
    fun registerAppUserModelId(appId: String = "com.prince.artwallpaper") {
        if (!isWindows) return
        try {
            val res = shell32?.SetCurrentProcessExplicitAppUserModelID(WString(appId))
            if (res == 0) {
                logger.info("Windows AppUserModelID registered: $appId")
            }
        } catch (e: Exception) {
            logger.debug("Could not register AppUserModelID: ${e.message}")
        }
    }

    /**
     * Brings an AWT window to the front and grants it foreground focus on Windows.
     */
    fun bringToFront(window: Window) {
        try {
            window.toFront()
            window.requestFocus()
            if (isWindows) {
                val ptr = Native.getComponentPointer(window) ?: return
                val hwnd = HWND(ptr)
                user32?.ShowWindow(hwnd, 9) // SW_RESTORE = 9
                user32?.SetForegroundWindow(hwnd)
            }
        } catch (_: Exception) {}
    }

    /**
     * Toggles the Win32 immersive dark title bar on the given AWT window.
     */
    fun applyDarkTitleBar(window: Window, dark: Boolean) {
        if (!isWindows) return
        val api = dwmApi ?: return
        try {
            val ptr = Native.getComponentPointer(window) ?: return
            val hwnd = HWND(ptr)
            val value = Memory(4).also { it.setInt(0, if (dark) 1 else 0) }
            // DWMWA_USE_IMMERSIVE_DARK_MODE = 20
            api.DwmSetWindowAttribute(hwnd, 20, value, 4)
        } catch (_: Exception) {}
    }

    /**
     * Windows 11 rounded window corners.
     * DWMWA_WINDOW_CORNER_PREFERENCE = 33, value 2 = DWMWCP_ROUND
     */
    fun applyRoundedCorners(window: Window) {
        if (!isWindows) return
        val api = dwmApi ?: return
        try {
            val ptr = Native.getComponentPointer(window) ?: return
            val hwnd = HWND(ptr)
            val value = Memory(4).also { it.setInt(0, 2) } // DWMWCP_ROUND
            api.DwmSetWindowAttribute(hwnd, 33, value, 4)
        } catch (_: Exception) {}
    }
}
