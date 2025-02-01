import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.*
import java.awt.*
import javax.swing.SwingUtilities
import kotlin.system.exitProcess
import androidx.compose.ui.res.painterResource

fun main(args: Array<String>) {
    val settings = Settings.load()
    val startMinimized = args.contains("--minimized") && !settings.isFirstRun
    
    // Initialize components
    val wallpaperService = WindowsWallpaperService()
    val artworkProvider = ArtworkProvider()
    val artworkStorage = ArtworkStorageManager()
    val historyManager = HistoryManager()
    
    val wallpaperManager = WallpaperManager(
        wallpaperService = wallpaperService,
        artworkProvider = artworkProvider,
        artworkStorage = artworkStorage,
        historyManager = historyManager,
        settings = settings
    )
    
    val serviceController = ServiceController(
        wallpaperManager = wallpaperManager,
        artworkStorage = artworkStorage,
        historyManager = historyManager,
        connectivityChecker = ConnectivityChecker(),
        artworkProvider = artworkProvider
    )

    // Only start service if not first run
    if (!settings.isFirstRun) {
        serviceController.startService()
    }

    var windowVisibilityCallback: ((Boolean) -> Unit)? = null
    
    // Initialize system tray before application
    val systemTray = SystemTrayMenu(
        serviceController = serviceController,
        onShowWindow = { 
            windowVisibilityCallback?.invoke(true)
            true
        }
    )
    systemTray.show()

    application {
        var isWindowVisible by remember { mutableStateOf(!startMinimized) }
        
        // Set the callback to control window visibility
        windowVisibilityCallback = { visible -> 
            isWindowVisible = visible 
        }

        Window(
            onCloseRequest = {
                if (settings.isFirstRun) {
                    isWindowVisible = false
                    settings.copy(isFirstRun = false).save()
                } else {
                    isWindowVisible = false
                    systemTray.displayNotification(
                        "Art Wallpaper is still running",
                        "The application has been minimized to the system tray. Right click the tray icon to reopen."
                    )
                }
            },
            title = "Art Wallpaper",
            state = rememberWindowState(
                width = 960.dp,
                height = 640.dp,
                isMinimized = startMinimized
            ),
            visible = isWindowVisible,
            icon = painterResource("tray_icon.png")
        ) {
            App(
                wallpaperManager = wallpaperManager,
                serviceController = serviceController,
                settings = settings,
                onSettingsChange = { newSettings ->
                    newSettings.save()
                    // Restart service to apply new settings
                    serviceController.restartService()
                }
            )
        }
    }

    // Enable auto-start on first run
    if (!settings.hasEnabledAutoStart) {
        WindowsAutoStart.enable()
        settings.copy(hasEnabledAutoStart = true).save()
    }

    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking {
            serviceController.stopService()
            scope.cancel()
        }
    })
}

@Composable
fun App(
    wallpaperManager: WallpaperManager,
    serviceController: ServiceController,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit
) {
    ArtWallpaperTheme {
        val scope = rememberCoroutineScope()
        val currentArtwork by wallpaperManager.currentArtwork.collectAsState()
        
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            MainScreen(
                currentArtwork = currentArtwork,
                serviceController = serviceController,
                settings = settings,
                onSettingsChange = onSettingsChange
            )
        }
    }
}

private fun setupSystemTray(wallpaperManager: WallpaperManager) {
    if (!SystemTray.isSupported()) return

    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    SwingUtilities.invokeLater {
        val tray = SystemTray.getSystemTray()
        val image = Toolkit.getDefaultToolkit().createImage(
            {}::class.java.getResource("/tray_icon.png")
        )

        val popup = PopupMenu().apply {
            add(MenuItem("Exit").apply {
                addActionListener {
                    scope.cancel()
                    wallpaperManager.stop()
                    exitProcess(0)
                }
            })
        }

        val trayIcon = TrayIcon(image, "Art Wallpaper", popup).apply {
            isImageAutoSize = true
        }

        try {
            tray.add(trayIcon)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
} 