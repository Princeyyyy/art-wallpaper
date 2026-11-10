import org.slf4j.LoggerFactory

/**
 * macOS platform integration for ArtWallpaper.
 *
 * 1. Safe window activation and foreground focus via native macOS AppleScript.
 * 2. Branded notifications to macOS Notification Center targeting the app's bundle ID.
 *
 * Uses osascript subprocess to guarantee 100% JVM crash-safety across all architectures
 * (Intel x86_64 and Apple Silicon arm64 / M-series).
 */
object MacPlatform {

    private val logger = LoggerFactory.getLogger(MacPlatform::class.java)
    val isMac = System.getProperty("os.name", "").lowercase().contains("mac")

    /**
     * No-op on modern macOS: allows standard Dock presentation and window focus,
     * avoiding dangerous variadic JNA calls to the Objective-C runtime on ARM64 / Apple Silicon.
     */
    fun hideFromDock() {
        // Preserved as safe no-op for API compatibility
    }

    /**
     * Activates the application and brings its windows frontmost.
     */
    fun activateApp() {
        if (!isMac) return
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
            logger.info("macOS: application activated frontmost")
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
