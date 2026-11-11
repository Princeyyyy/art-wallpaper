import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.awt.GraphicsEnvironment

/**
 * macOS platform integration for ArtWallpaper.
 *
 * 1. Configure AWT UIElement agent mode and native template rendering before toolkit init.
 * 2. Safe window activation and foreground focus via JDK Desktop API with AppleScript fallback.
 * 3. Branded notifications to macOS Notification Center targeting the app's bundle ID.
 *
 * Avoids variadic JNA calls to objc_msgSend to guarantee 100% JVM crash-safety across
 * all architectures (Intel x86_64 and Apple Silicon arm64 / M-series).
 */
object MacPlatform {

    private val logger = LoggerFactory.getLogger(MacPlatform::class.java)
    val isMac = System.getProperty("os.name", "").lowercase().contains("mac")

    /**
     * Configures macOS AWT properties before Swing/AWT initialization.
     * Must be invoked at application startup prior to creating SystemTray or Window.
     */
    fun configureDesktopEnvironment() {
        if (!isMac) return
        // Hide from Dock and Cmd+Tab switcher cleanly using JDK's native agent flag
        // (complements LSUIElement=true in Info.plist, including unbundled dev runs).
        System.setProperty("apple.awt.UIElement", "true")
        // Enable native macOS template rendering so crisp monochrome tray glyphs
        // adapt cleanly to Dark, Light, and wallpaper-tinted Retina menu bars.
        System.setProperty("apple.awt.enableTemplateImages", "true")
        System.setProperty("apple.awt.application.name", "Art Wallpaper")
    }

    /**
     * Preserved API compatibility hook that reinforces Dock-hidden agent properties.
     */
    fun hideFromDock() {
        configureDesktopEnvironment()
    }

    /**
     * Activates the application and brings its windows frontmost.
     */
    fun activateApp() {
        if (!isMac) return
        if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().requestForeground(true)
                logger.info("macOS: application activated via Desktop.requestForeground")
                return
            } catch (e: Exception) {
                logger.debug("macOS: Desktop.requestForeground unavailable: ${e.message}")
            }
        }
        try {
            val bundleId = AppInfo.BUNDLE_ID
            val script = """
                try
                    tell application id "$bundleId" to activate
                on error
                    tell application "Art Wallpaper" to activate
                end try
            """.trimIndent()

            ProcessBuilder("osascript", "-e", script)
                .redirectErrorStream(true)
                .start()
            logger.info("macOS: application activated frontmost via AppleScript")
        } catch (e: Exception) {
            logger.debug("macOS: could not activate app: ${e.message}")
        }
    }

    /**
     * Posts a notification to macOS Notification Center.
     * Targets the app's bundle identifier so macOS displays the real app icon.
     */
    fun showNotification(title: String, body: String) {
        if (!isMac) return
        try {
            val safeTitle = title.replace("\"", "'").replace("\\", "")
            val safeBody  = body.replace("\"", "'").replace("\\", "")
            val bundleId  = AppInfo.BUNDLE_ID

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
