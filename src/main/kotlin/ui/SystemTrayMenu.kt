import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import service.ServiceController
import service.UpdateManager
import service.UpdateStatus
import java.awt.*
import java.awt.image.BaseMultiResolutionImage
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

class SystemTrayMenu(
    private val serviceController: ServiceController,
    private val onShowWindow: () -> Boolean,
    private val updateManager: UpdateManager? = null,
    private val onOpenUpdateDialog: (() -> Unit)? = null
) {
    private val logger = LoggerFactory.getLogger(SystemTrayMenu::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var trayIcon: TrayIcon? = null
    private var pauseResumeItem: MenuItem? = null
    private var currentArtItem: MenuItem? = null
    private var updateMenuItem: MenuItem? = null
    private var latestArtworkSummary: String = "Art Wallpaper"
    private var latestAvailableVersion: String? = null

    companion object {
        private val MAC_ICON_SIZES = intArrayOf(16, 18, 20, 22, 24, 32, 36, 40, 44, 48, 64)
        private val DEFAULT_ICON_SIZES = intArrayOf(16, 20, 24, 32, 36, 40, 48, 64, 96, 128)

        fun resolveTrayIconPath(isMac: Boolean, isDark: Boolean, size: Int): String {
            return if (isMac) {
                val useTemplateMask = java.lang.Boolean.getBoolean("apple.awt.enableTemplateImages")
                val color = if (!isDark || useTemplateMask) "black" else "white"
                val selected = MAC_ICON_SIZES.firstOrNull { size <= it } ?: MAC_ICON_SIZES.last()
                "/tray/tray-mono-$color-$selected.png"
            } else {
                val selected = DEFAULT_ICON_SIZES.firstOrNull { size <= it } ?: DEFAULT_ICON_SIZES.last()
                "/tray/tray-$selected.png"
            }
        }

        private fun readImageResource(path: String): BufferedImage? {
            val url = SystemTrayMenu::class.java.getResource(path) ?: return null
            return try {
                ImageIO.read(url)
            } catch (_: Exception) {
                null
            }
        }

        fun loadTrayImage(size: Dimension, isMac: Boolean, isDark: Boolean): Image? {
            val targetWidth = size.width.coerceAtLeast(16)
            val basePath = resolveTrayIconPath(isMac, isDark, targetWidth)
            val baseImage = readImageResource(basePath)
                ?: readImageResource("/tray_icon.png")
                ?: return null

            val hiDpiPath = resolveTrayIconPath(isMac, isDark, targetWidth * 2)
            val hiDpiImage = if (hiDpiPath != basePath) readImageResource(hiDpiPath) else null
            if (hiDpiImage != null && hiDpiImage.width > baseImage.width) {
                return BaseMultiResolutionImage(baseImage, hiDpiImage)
            }

            if (isMac) {
                return baseImage
            }

            return if (baseImage.width == size.width && baseImage.height == size.height) {
                baseImage
            } else {
                baseImage.getScaledInstance(size.width, size.height, Image.SCALE_SMOOTH)
            }
        }
    }

    /**
     * Dynamically updates the macOS menu bar icon to match OS Dark/Light appearance.
     */
    fun updateIconTheme(isDark: Boolean) {
        val isMac = System.getProperty("os.name", "").lowercase().contains("mac")
        if (!isMac || !SystemTray.isSupported()) return
        if (java.lang.Boolean.getBoolean("apple.awt.enableTemplateImages")) return
        SwingUtilities.invokeLater {
            val tray = SystemTray.getSystemTray()
            val newImage = loadTrayImage(tray.trayIconSize, isMac, isDark)
            if (newImage != null) {
                trayIcon?.image = newImage
            }
        }
    }

    /**
     * Shows a desktop notification only if enabled in user preferences.
     *
     * Platform routing:
     *   macOS   -> Notification Center via osascript (supports Do Not Disturb natively).
     *   Windows -> AWT TrayIcon balloon (classic; works on all Windows versions).
     */
    fun displayNotification(title: String, message: String) {
        if (!Settings.currentSettings.value.notificationsEnabled) return
        val isMac = System.getProperty("os.name", "").lowercase().contains("mac")
        if (isMac) {
            MacPlatform.showNotification(title, message)
        } else {
            trayIcon?.displayMessage(title, message, TrayIcon.MessageType.INFO)
        }
    }

    /**
     * Posts a native OS notification announcing a newly discovered application release.
     */
    fun displayUpdateNotification(available: UpdateStatus.Available) {
        val settings = Settings.currentSettings.value
        if (!settings.notificationsEnabled || !settings.autoCheckUpdates) return
        val title = "Art Wallpaper v${available.latestVersion} Available"
        val isMac = System.getProperty("os.name", "").lowercase().contains("mac")
        if (isMac) {
            MacPlatform.showNotification(
                title,
                "A new release is ready. Open Art Wallpaper from the menu bar to review and install."
            )
        } else {
            trayIcon?.displayMessage(
                title,
                "A new release is ready to install. Click here or open the tray menu to update.",
                TrayIcon.MessageType.INFO
            )
        }
    }

    private fun refreshTooltip() {
        val updateSuffix = latestAvailableVersion?.let { " | Update v$it ready" } ?: ""
        val base = latestArtworkSummary.ifBlank { "Art Wallpaper" }
        trayIcon?.toolTip = "$base$updateSuffix".take(120)
    }

    fun show() {
        if (!SystemTray.isSupported()) return

        SwingUtilities.invokeLater {
            val tray = SystemTray.getSystemTray()
            val trayIconSize = tray.trayIconSize
            val isMac = System.getProperty("os.name", "").lowercase().contains("mac")
            val isDark = SystemThemeDetector.isDark()

            val image = loadTrayImage(trayIconSize, isMac, isDark)
                ?: throw IllegalStateException("Could not load tray icon for size $trayIconSize")

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
                            logger.warn("Failed to open main window from tray", e)
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
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                logger.warn("Failed to change wallpaper from tray: ${e.message}")
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
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                logger.warn("Failed to toggle auto-update from tray: ${e.message}")
                            }
                        }
                    }
                }
                add(pauseResumeItem)

                if (updateManager != null) {
                    addSeparator()
                    updateMenuItem = MenuItem("Check for Updates...").apply {
                        addActionListener {
                            val currentStatus = updateManager.status.value
                            if (currentStatus is UpdateStatus.Available ||
                                currentStatus is UpdateStatus.Downloading ||
                                currentStatus is UpdateStatus.ReadyToInstall
                            ) {
                                SwingUtilities.invokeLater {
                                    onOpenUpdateDialog?.invoke() ?: onShowWindow()
                                }
                            } else {
                                scope.launch {
                                    try {
                                        val result = updateManager.checkForUpdates(silent = false)
                                        SwingUtilities.invokeLater {
                                            when (result) {
                                                is UpdateStatus.Available -> onOpenUpdateDialog?.invoke() ?: onShowWindow()
                                                is UpdateStatus.UpToDate -> displayNotification(
                                                    "Art Wallpaper",
                                                    "You are running the latest version (v${AppInfo.version})."
                                                )
                                                is UpdateStatus.Error -> displayNotification(
                                                    "Art Wallpaper",
                                                    "Could not check for updates right now."
                                                )
                                                else -> Unit
                                            }
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        logger.warn("Tray update check failed: ${e.message}")
                                    }
                                }
                            }
                        }
                    }
                    add(updateMenuItem)
                }

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
                        } catch (_: Exception) {
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
                        logger.warn("Tray activation failed", e)
                    }
                }
            }

            try {
                trayIcon?.let { tray.add(it) }
            } catch (e: Exception) {
                logger.warn("Unable to register tray icon", e)
            }

            // Sync tray state with background service, artwork changes, and updates
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
                latestArtworkSummary = if (art != null) "Art Wallpaper: $title$artist" else "Art Wallpaper"

                SwingUtilities.invokeLater {
                    currentArtItem?.label = if (art != null) "Current: ${title.take(30)}" else "Art Wallpaper"
                    refreshTooltip()
                }
            }
        }

        // Observe release update status to surface pending updates right in the tray menu
        if (updateManager != null) {
            scope.launch {
                updateManager.status.collect { status ->
                    SwingUtilities.invokeLater {
                        when (status) {
                            is UpdateStatus.Available -> {
                                latestAvailableVersion = status.latestVersion
                                updateMenuItem?.label = "Update to v${status.latestVersion}..."
                                updateMenuItem?.isEnabled = true
                            }
                            is UpdateStatus.Checking -> {
                                updateMenuItem?.label = "Checking for Updates..."
                                updateMenuItem?.isEnabled = false
                            }
                            is UpdateStatus.Downloading -> {
                                updateMenuItem?.label = "Downloading Update (${(status.progress * 100).toInt()}%)..."
                                updateMenuItem?.isEnabled = true
                            }
                            is UpdateStatus.Installing -> {
                                updateMenuItem?.label = "Installing Update..."
                                updateMenuItem?.isEnabled = false
                            }
                            else -> {
                                latestAvailableVersion = null
                                updateMenuItem?.label = "Check for Updates..."
                                updateMenuItem?.isEnabled = true
                            }
                        }
                        refreshTooltip()
                    }
                }
            }
        }
    }
}
