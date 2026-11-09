import com.sun.jna.Library
import com.sun.jna.Native
import org.slf4j.LoggerFactory

/**
 * macOS platform integration for ArtWallpaper.
 *
 * 1. Hide the app from the Dock and Cmd+Tab switcher at runtime (`setActivationPolicy: 1`).
 * 2. Activate application and bring window frontmost (`activateIgnoringOtherApps: YES`).
 * 3. Post branded notifications to macOS Notification Center via `NSUserNotificationCenter`
 *    (displaying the app's real `icon.icns`), with osascript fallback.
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
     * Activates the application and brings its windows frontmost.
     * Essential for LSUIElement=1 / Accessory apps when opening windows.
     */
    fun activateApp() {
        if (!isMac) return
        val lib = objc ?: return
        try {
            val nsAppClass = lib.objc_getClass("NSApplication")
            val sharedAppSel = lib.sel_registerName("sharedApplication")
            val nsApp = lib.objc_msgSend(nsAppClass, sharedAppSel)
            val activateSel = lib.sel_registerName("activateIgnoringOtherApps:")
            lib.objc_msgSend(nsApp, activateSel, 1L)
            logger.info("macOS: application activated frontmost")
        } catch (e: Exception) {
            logger.debug("macOS: could not activate app: ${e.message}")
        }
    }

    /**
     * Posts a notification to macOS Notification Center.
     * Uses NSUserNotificationCenter so macOS attaches the app's real icon.icns logo.
     */
    fun showNotification(title: String, body: String) {
        if (!isMac) return

        // Approach 1: Native Cocoa NSUserNotificationCenter (shows app bundle icon)
        val lib = objc
        if (lib != null) {
            try {
                val centerClass = lib.objc_getClass("NSUserNotificationCenter")
                val defaultCenterSel = lib.sel_registerName("defaultUserNotificationCenter")
                val center = lib.objc_msgSend(centerClass, defaultCenterSel)

                if (center != 0L) {
                    val notifClass = lib.objc_getClass("NSUserNotification")
                    val allocSel = lib.sel_registerName("alloc")
                    val initSel = lib.sel_registerName("init")
                    val notifAlloc = lib.objc_msgSend(notifClass, allocSel)
                    val notif = lib.objc_msgSend(notifAlloc, initSel)

                    val strClass = lib.objc_getClass("NSString")
                    val strWithUtf8Sel = lib.sel_registerName("stringWithUTF8String:")

                    val titleNs = lib.objc_msgSend(strClass, strWithUtf8Sel, title)
                    val bodyNs = lib.objc_msgSend(strClass, strWithUtf8Sel, body)

                    val setTitleSel = lib.sel_registerName("setTitle:")
                    val setInfoSel = lib.sel_registerName("setInformativeText:")
                    val deliverSel = lib.sel_registerName("deliverNotification:")

                    lib.objc_msgSend(notif, setTitleSel, titleNs)
                    lib.objc_msgSend(notif, setInfoSel, bodyNs)
                    lib.objc_msgSend(center, deliverSel, notif)
                    logger.info("macOS: posted notification via NSUserNotificationCenter")
                    return
                }
            } catch (e: Exception) {
                logger.debug("Cocoa notification failed, falling back to osascript: ${e.message}")
            }
        }

        // Approach 2: AppleScript fallback
        try {
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
