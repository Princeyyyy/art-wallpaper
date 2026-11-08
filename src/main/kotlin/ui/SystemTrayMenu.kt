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
    // FIX #11: Removed the dead PopupMenu created in init that was immediately
    // overwritten (and its action listeners orphaned) when show() built a new one.
    // trayIcon is now initialised properly inside show() and nullable until then.
    private var trayIcon: TrayIcon? = null

    fun displayNotification(title: String, message: String) {
        trayIcon?.displayMessage(title, message, TrayIcon.MessageType.NONE)
    }

    fun show() {
        if (!SystemTray.isSupported()) return

        SwingUtilities.invokeLater {
            val tray = SystemTray.getSystemTray()
            val image = Toolkit.getDefaultToolkit().createImage(
                SystemTrayMenu::class.java.getResource("/tray_icon.png")
            )?.let { img ->
                val tracker = MediaTracker(Canvas())
                tracker.addImage(img, 0)
                try {
                    tracker.waitForAll()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                val trayIconSize = tray.trayIconSize
                img.getScaledInstance(trayIconSize.width, trayIconSize.height, Image.SCALE_SMOOTH)
            } ?: throw IllegalStateException("Could not load tray icon")

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
