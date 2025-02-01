import service.ServiceController
import java.awt.*
import javax.swing.SwingUtilities

class SystemTrayMenu(
    private val serviceController: ServiceController,
    private val onShowWindow: () -> Boolean
) {
    private var trayIcon: TrayIcon? = null

    fun displayNotification(title: String, message: String) {
        trayIcon?.displayMessage(
            title,
            message,
            TrayIcon.MessageType.NONE
        )
    }

    fun show() {
        if (!SystemTray.isSupported()) return

        SwingUtilities.invokeLater {
            val tray = SystemTray.getSystemTray()
            val image = Toolkit.getDefaultToolkit().createImage(
                SystemTrayMenu::class.java.getResource("/tray_icon.png")
            )?.let { img ->
                // Ensure image is fully loaded
                MediaTracker(Canvas()).apply {
                    addImage(img, 0)
                    try {
                        waitForAll()
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
                // Scale image to system tray icon size
                val trayIconSize = tray.trayIconSize
                img.getScaledInstance(trayIconSize.width, trayIconSize.height, Image.SCALE_SMOOTH)
            } ?: throw IllegalStateException("Could not load tray icon")

            val popup = PopupMenu().apply {
                add(MenuItem("Open Art Wallpaper").apply {
                    addActionListener { onShowWindow() }
                })
                addSeparator()
                
                add(MenuItem("Pause Auto-Update").apply {
                    addActionListener { serviceController.stopService() }
                })
                add(MenuItem("Resume Auto-Update").apply {
                    addActionListener { serviceController.startService() }
                })
                
                addSeparator()
                
                add(MenuItem("Exit").apply {
                    addActionListener {
                        serviceController.stopService()
                        System.exit(0)
                    }
                })
            }

            trayIcon = TrayIcon(image, "Art Wallpaper", popup).apply {
                isImageAutoSize = true
                addActionListener { onShowWindow() }
            }

            try {
                tray.add(trayIcon!!)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
} 