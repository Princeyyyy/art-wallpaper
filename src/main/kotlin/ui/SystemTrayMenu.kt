import kotlinx.coroutines.*
import service.ServiceController
import java.awt.*
import javax.swing.SwingUtilities
import javax.imageio.ImageIO

class SystemTrayMenu(
    private val serviceController: ServiceController,
    private val onShowWindow: () -> Boolean
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var trayIcon: TrayIcon? = null

    fun displayNotification(title: String, message: String) {
        trayIcon?.displayMessage(title, message, TrayIcon.MessageType.NONE)
    }

    fun show() {
        if (!SystemTray.isSupported()) return

        SwingUtilities.invokeLater {
            val tray = SystemTray.getSystemTray()
            val trayIconSize = tray.trayIconSize

            // Pick the pixel-perfect tray asset matching current Windows DPI scale
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
            } ?: throw IllegalStateException("Could not load tray icon from $iconPath or /tray_icon.png")

            val popup = PopupMenu().apply {
                add(MenuItem("Open Art Wallpaper").apply {
                    addActionListener {
                        try {
                            SwingUtilities.invokeLater { onShowWindow() }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                })
                addSeparator()

                add(MenuItem("Pause Auto-Update").apply {
                    addActionListener {
                        try {
                            scope.launch {
                                serviceController.stopService()
                                displayNotification("Art Wallpaper", "Auto-update paused")
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            displayNotification("Art Wallpaper", "Failed to pause auto-update")
                        }
                    }
                })
                add(MenuItem("Resume Auto-Update").apply {
                    addActionListener {
                        try {
                            scope.launch {
                                serviceController.startService()
                                displayNotification("Art Wallpaper", "Auto-update resumed")
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            displayNotification("Art Wallpaper", "Failed to resume auto-update")
                        }
                    }
                })

                addSeparator()

                add(MenuItem("Exit").apply {
                    addActionListener {
                        try {
                            scope.launch {
                                serviceController.stopService()
                                scope.cancel()
                                System.exit(0)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
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
        }
    }
}
