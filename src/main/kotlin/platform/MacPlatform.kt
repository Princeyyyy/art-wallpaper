import com.sun.jna.Library
import com.sun.jna.Native
import org.slf4j.LoggerFactory

/**
 * macOS platform integration for ArtWallpaper.
 *
 * Covers two critical platform-native requirements:
 *  1. Hide the app from the Dock and Cmd+Tab switcher at runtime
 *     (the Info.plist LSUIElement=1 handles the packaged DMG build;
 *      this runtime call covers dev mode and edge cases).
 *  2. Post notifications to macOS Notification Center via osascript
 *     instead of the AWT balloon (which never appears in NC on macOS).
 */
object MacPlatform {

    private val logger = LoggerFactory.getLogger(MacPlatform::class.java)
    private val isMac = System.getProperty("os.name", "").lowercase().contains("mac")

    // ── Minimal Objective-C runtime bridge via JNA ──────────────────────────

    private interface ObjCLibrary : Library {
        fun objc_getClass(className: String): Long
        fun sel_registerName(selectorName: String): Long
        fun objc_msgSend(receiver: Long, sel: Long, vararg args: Any?): Long
    }

    private val objc: ObjCLibrary? by lazy {
        if (!isMac) null
        else try {
            Native.load("objc", ObjCLibrary::class.java) as ObjCLibrary
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Sets NSApplication activation policy to Accessory (1), which removes
     * the app from the Dock and Cmd+Tab. Equivalent to LSUIElement=1 at runtime.
     *
     * MUST be called before AWT/Swing toolkit initialises (before `application { }`).
     */
    fun hideFromDock() {
        if (!isMac) return
        val lib = objc ?: return
        try {
            val nsAppClass  = lib.objc_getClass("NSApplication")
            val sharedAppSel = lib.sel_registerName("sharedApplication")
            val nsApp       = lib.objc_msgSend(nsAppClass, sharedAppSel)
            val setPolicySel = lib.sel_registerName("setActivationPolicy:")
            // NSApplicationActivationPolicyAccessory = 1
            lib.objc_msgSend(nsApp, setPolicySel, 1L)
            logger.info("macOS: activation policy set to Accessory (hidden from Dock)")
        } catch (e: Exception) {
            logger.warn("macOS: could not set activation policy: ${e.message}")
        }
    }

    /**
     * Posts a notification to macOS Notification Center.
     * Uses osascript — the only cross-JVM approach without a native framework.
     * Respects system Do Not Disturb automatically (the OS handles that).
     */
    fun showNotification(title: String, body: String) {
        if (!isMac) return
        try {
            // Sanitise: escape double-quotes so we don't break the osascript
            val safeTitle = title.replace("\"", "'")
            val safeBody  = body.replace("\"", "'")
            ProcessBuilder(
                "osascript", "-e",
                """display notification "$safeBody" with title "$safeTitle" """
            ).redirectErrorStream(true).start()
        } catch (e: Exception) {
            logger.warn("macOS notification failed: ${e.message}")
        }
    }
}
