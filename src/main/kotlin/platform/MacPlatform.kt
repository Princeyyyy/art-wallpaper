import com.sun.jna.Library
import com.sun.jna.Native
import org.slf4j.LoggerFactory

/**
 * macOS platform integration for ArtWallpaper.
 *
 * 1. Hide the app from the Dock and Cmd+Tab switcher at runtime (`setActivationPolicy: 1`).
 * 2. Activate application and bring window frontmost (`activateIgnoringOtherApps: YES`).
 * 3. Post branded notifications to macOS Notification Center targeting the app's bundle ID.
 */
object MacPlatform {

    private val logger = LoggerFactory.getLogger(MacPlatform::class.java)
    val isMac = System.getProperty("os.name", "").lowercase().contains("mac")

    // ── Minimal Objective-C runtime bridge via JNA ──────────────────────────

    internal interface ObjCLibrary : Library {
        fun objc_getClass(className: String): Long
        fun sel_registerName(selectorName: String): Long
        fun objc_msgSend(receiver: Long, sel: Long, vararg args: Any?): Long
    }

    internal val objc: ObjCLibrary? by lazy {
        if (!isMac) null
        else try {
            Native.load("objc", ObjCLibrary::class.java) as ObjCLibrary
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Sets NSApplication activation policy to Accessory (1), which removes
     * the app from the Dock and Cmd+Tab.
     */
    fun hideFromDock() {
        if (!isMac) return
        val lib = objc ?: return
        try {
            val nsAppClass = lib.objc_getClass("NSApplication")
            if (nsAppClass == 0L) return
            val sharedAppSel = lib.sel_registerName("sharedApplication")
            val nsApp = lib.objc_msgSend(nsAppClass, sharedAppSel)
            if (nsApp == 0L) return
            val setPolicySel = lib.sel_registerName("setActivationPolicy:")
            // NSApplicationActivationPolicyAccessory = 1
            lib.objc_msgSend(nsApp, setPolicySel, 1L)
            logger.info("macOS: activation policy set to Accessory (hidden from Dock)")
        } catch (e: Exception) {
            logger.warn("macOS: could not set activation policy: ${e.message}")
        }
    }

    /**
     * Activates the application and brings its windows frontmost.
     * Essential for LSUIElement=1 / Accessory apps when opening windows.
     */
    fun activateApp() {
        if (!isMac) return
        val lib = objc ?: return
        try {
            val nsAppClass = lib.objc_getClass("NSApplication")
            if (nsAppClass == 0L) return
            val sharedAppSel = lib.sel_registerName("sharedApplication")
            val nsApp = lib.objc_msgSend(nsAppClass, sharedAppSel)
            if (nsApp == 0L) return
            val activateSel = lib.sel_registerName("activateIgnoringOtherApps:")
            lib.objc_msgSend(nsApp, activateSel, 1L)
            logger.info("macOS: application activated frontmost")
        } catch (e: Exception) {
            logger.debug("macOS: could not activate app: ${e.message}")
        }
    }

    /**
     * Posts a notification to macOS Notification Center.
     * Targets the app's bundle identifier so macOS displays the real app icon.
     * Uses osascript subprocess to guarantee 100% JVM crash-safety on all architectures (x86_64 and arm64).
     */
    fun showNotification(title: String, body: String) {
        if (!isMac) return
        try {
            val safeTitle = title.replace("\"", "'").replace("\\", "")
            val safeBody  = body.replace("\"", "'").replace("\\", "")
            val bundleId  = AppInfo.BUNDLE_ID

            // Try targeting the bundle ID for native app icon, with universal fallback
            val script = """
                try
                    tell application id "$bundleId" to display notification "$safeBody" with title "$safeTitle"
                on error
                    display notification "$safeBody" with title "$safeTitle"
                end try
            """.trimIndent()

            ProcessBuilder("osascript", "-e", script)
                .redirectErrorStream(true)
                .start()
            logger.info("macOS: notification sent")
        } catch (e: Exception) {
            logger.warn("macOS notification failed: ${e.message}")
        }
    }
}
