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
import androidx.compose.ui.res.painterResource
import service.ConnectivityChecker
import service.HistoryManager
import service.ServiceController
import service.WallpaperManager

fun main(args: Array<String>) {
    val settings = Settings.load()
    val startMinimized = args.contains("--minimized") && !settings.isFirstRun
    val isAutoStart = args.contains("--autostart")
    
    // Initialize components
    val wallpaperService = WindowsWallpaperService()
    val historyManager = HistoryManager()
    val artworkProvider = ArtworkProvider(
        historyManager = historyManager,
        connectivityChecker = ConnectivityChecker()
    )
    val artworkStorage = ArtworkStorageManager()
    
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
        artworkProvider = artworkProvider
    )

    // Start service if auto-starting or not first run
    if (isAutoStart || !settings.isFirstRun) {
        serviceController.startService(isAutoStart)
    }

    // If auto-starting, only run the service without UI
    if (isAutoStart) {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        
        Runtime.getRuntime().addShutdownHook(Thread {
            runBlocking {
                serviceController.stopService()
                scope.cancel()
            }
        })
        
        runBlocking {
            // Keep the application running
            while (true) {
                delay(Long.MAX_VALUE)
            }
        }
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
        rememberCoroutineScope()
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