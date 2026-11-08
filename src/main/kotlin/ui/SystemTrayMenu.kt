import kotlinx.coroutines.*
import service.ServiceController
import java.awt.*
import javax.swing.SwingUtilities

class SystemTrayMenu(
    private val serviceController: ServiceController,
    private val onShowWindow: () -> Boolean
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var trayIcon: TrayIcon? = null
    private var pauseResumeItem: MenuItem? = null
    private var currentArtItem: MenuItem? = null

    /**
     * Shows a desktop notification only if enabled in user preferences.
     *
     * Platform routing:
     *   macOS  → Notification Center via osascript (supports Do Not Disturb natively).
     *   Windows → AWT TrayIcon balloon (classic; works on all Windows versions).
     */
    fun displayNotification(title: String, message: String) {
        if (!Settings.currentSettings.value.notificationsEnabled) return
        val isMac = System.getProperty("os.name", "").lowercase().contains("mac")
        if (isMac) {
            MacPlatform.showNotification(title, message)
        } else {
            trayIcon?.displayMessage(title, message, TrayIcon.MessageType.NONE)
        }
    }

    fun show() {
        if (!SystemTray.isSupported()) return

        SwingUtilities.invokeLater {
            val tray = SystemTray.getSystemTray()
            val trayIconSize = tray.trayIconSize

            // Select matching pixel-perfect tray asset for the active display DPI
            val iconPath = when {
                trayIconSize.width <= 16 -> "/tray/tray-16.png"
                trayIconSize.width <= 20 -> "/tray/tray-20.png"
                trayIconSize.width <= 24 -> "/tray/tray-24.png"
                trayIconSize.width <= 32 -> "/tray/tray-32.png"
                trayIconSize.width <= 48 -> "/tray/tray-48.png"
                else -> "/tray/tray-64.png"
            }
            val resourceUrl = SystemTrayMenu::class.java.getResource(iconPath)
                ?: SystemTrayMenu::class.java.getResource("/tray_icon.png")

            val image = resourceUrl?.let { url ->
                Toolkit.getDefaultToolkit().createImage(url)?.let { img ->
                    val tracker = MediaTracker(Canvas())
                    tracker.addImage(img, 0)
                    try {
                        tracker.waitForAll()
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                    img.getScaledInstance(trayIconSize.width, trayIconSize.height, Image.SCALE_SMOOTH)
                }
            } ?: throw IllegalStateException("Could not load tray icon from $iconPath")

            val popup = PopupMenu().apply {
                // Header showing current artwork title
                currentArtItem = MenuItem("Art Wallpaper").apply {
                    isEnabled = false
                }
                add(currentArtItem)
                addSeparator()

                // Open Window
                add(MenuItem("Open Art Wallpaper").apply {
                    font = font?.deriveFont(Font.BOLD) ?: Font("Dialog", Font.BOLD, 12)
                    addActionListener {
                        try {
                            SwingUtilities.invokeLater { onShowWindow() }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                })

                // Instant Wallpaper Refresh from Tray
                add(MenuItem("Next Wallpaper").apply {
                    addActionListener {
                        scope.launch {
                            try {
                                serviceController.nextWallpaper()
                                displayNotification("Art Wallpaper", "Changing wallpaper...")
                            } catch (e: Exception) {
                                e.printStackTrace()
                                displayNotification("Art Wallpaper", "Failed to change wallpaper: ${e.message}")
                            }
                        }
                    }
                })

                addSeparator()

                // Dynamic Pause / Resume item synchronized with ServiceController state
                pauseResumeItem = MenuItem("Pause Auto-Update").apply {
                    addActionListener {
                        scope.launch {
                            try {
                                if (serviceController.isServiceRunning.value) {
                                    serviceController.stopService()
                                    displayNotification("Art Wallpaper", "Auto-update paused")
                                } else {
                                    serviceController.startService()
                                    displayNotification("Art Wallpaper", "Auto-update resumed")
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                add(pauseResumeItem)

                addSeparator()

                // Exit
                add(MenuItem("Exit").apply {
                    addActionListener {
                        try {
                            scope.launch {
                                serviceController.stopService()
                                scope.cancel()
                                System.exit(0)
                            }
                        } catch (e: Exception) {
                            System.exit(1)
                        }
                    }
                })
            }

            trayIcon = TrayIcon(image, "Art Wallpaper", popup).apply {
                isImageAutoSize = true
                addActionListener {
                    try {
                        SwingUtilities.invokeLater { onShowWindow() }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            try {
                trayIcon?.let { tray.add(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Sync tray state with background service and artwork changes
            observeState()
        }
    }

    private fun observeState() {
        // Observe running state to dynamically toggle Pause/Resume label
        scope.launch {
            serviceController.isServiceRunning.collect { isRunning ->
                SwingUtilities.invokeLater {
                    pauseResumeItem?.label = if (isRunning) "Pause Auto-Update" else "Resume Auto-Update"
                }
            }
        }

        // Observe current artwork to update tooltip and header label
        scope.launch {
            serviceController.wallpaperManager.currentArtwork.collect { art ->
                val title = art?.second?.title ?: "Art Wallpaper"
                val artist = art?.second?.artist?.let { " - $it" } ?: ""
                val fullText = "$title$artist"

                SwingUtilities.invokeLater {
                    currentArtItem?.label = if (art != null) "Current: ${title.take(30)}" else "Art Wallpaper"
                    trayIcon?.toolTip = "Art Wallpaper: ${fullText.take(120)}"
                }
            }
        }
    }
}
