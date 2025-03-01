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
import java.nio.file.Path
import kotlin.io.path.*
import org.slf4j.LoggerFactory
import util.DataCleanup

val logger = LoggerFactory.getLogger("MainKt")

fun main(args: Array<String>) {
    setupGlobalExceptionHandler()
    
    // Add command line argument handling
    if (args.contains("--clear-data")) {
        DataCleanup.clearAllData()
        println("All application data cleared")
        System.exit(0)
        return
    }
    
    val lockFile = Path(System.getProperty("user.home"), ".artwallpaper", "app.lock")
    if (!ensureSingleInstance(lockFile)) {
        println("Application already running")
        System.exit(0)
        return
    }

    try {
        val settings = Settings.loadSavedSettings()
        val startMinimized = args.contains("--minimized") && !settings.isFirstRun
        val isAutoStart = args.contains("--autostart")
        
        // Don't start the service if it's first run or if we're not auto-starting
        val shouldStartService = !settings.isFirstRun && (isAutoStart || !startMinimized)
        
        // Initialize components
        val wallpaperService = WindowsWallpaperService()
        val historyManager = HistoryManager()
        val artworkStorage = ArtworkStorageManager()
        val artworkProvider = ArtworkProvider(
            historyManager = historyManager,
            connectivityChecker = ConnectivityChecker(),
            artworksDir = artworkStorage.getArtworksDir()
        )
        val wallpaperManager = WallpaperManager(
            wallpaperService = wallpaperService,
            artworkProvider = artworkProvider,
            artworkStorage = artworkStorage,
            historyManager = historyManager,
            settings = settings
        )
        
        val controller = ServiceController(
            wallpaperManager = wallpaperManager,
            artworkStorage = artworkStorage,
            historyManager = historyManager,
            artworkProvider = artworkProvider
        )

        // Setup system tray and window
        setupApplication(
            controller,
            settings,
            startMinimized,
            shouldStartService,
            lockFile,
            wallpaperManager
        )

    } catch (e: Exception) {
        logger.error("Failed to initialize application", e)
        System.exit(1)
    }
}

private fun setupGlobalExceptionHandler() {
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        logger.error("Uncaught exception in thread ${thread.name}", throwable)
    }
}

private fun ensureSingleInstance(lockFile: Path): Boolean {
    try {
        if (lockFile.exists()) {
            try {
                val pid = lockFile.readText().trim().toLong()
                if (ProcessHandle.of(pid).isPresent) {
                    return false
                }
            } catch (e: Exception) {
                // Invalid or stale lock file
            }
        }
        lockFile.parent.createDirectories()
        lockFile.writeText(ProcessHandle.current().pid().toString())
        Runtime.getRuntime().addShutdownHook(Thread {
            try {
                lockFile.deleteIfExists()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        })
        return true
    } catch (e: Exception) {
        return false
    }
}

// Create a single shutdown hook handler
private fun setupShutdownHook(controller: ServiceController, lockFile: Path) {
    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking {
            try {
                controller.cleanup()
                lockFile.deleteIfExists()
                logger.info("Application shutdown completed")
            } catch (e: Exception) {
                logger.error("Error during shutdown", e)
            }
        }
    })
}

private fun setupApplication(
    controller: ServiceController,
    settings: Settings,
    startMinimized: Boolean,
    shouldStartService: Boolean,
    lockFile: Path,
    wallpaperManager: WallpaperManager
) {
    var windowVisibilityCallback: ((Boolean) -> Unit)? = null
    
    // Initialize system tray
    val systemTray = SystemTrayMenu(
        serviceController = controller,
        onShowWindow = { 
            windowVisibilityCallback?.invoke(true)
            true
        }
    )
    systemTray.show()

    // Setup shutdown hook
    setupShutdownHook(controller, lockFile)

    // Start the service only if needed
    if (shouldStartService) {
        runBlocking {
            controller.startService()
        }
    }

    application {
        var isWindowVisible by remember { mutableStateOf(!startMinimized) }
        
        // Set the callback to control window visibility
        windowVisibilityCallback = { visible -> 
            isWindowVisible = visible 
        }

        // Start service if not already running
        LaunchedEffect(Unit) {
            if (!controller.isServiceRunning.value) {
                controller.startService()
            }
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
                serviceController = controller,
                settings = settings,
                onSettingsChange = { newSettings ->
                    newSettings.save()
                    controller.restartService()
                }
            )
        }
    }

    // Enable auto-start on first run
    if (!settings.hasEnabledAutoStart) {
        WindowsAutoStart.enable()
        settings.copy(hasEnabledAutoStart = true).save()
    }
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
                onSettingsChange = onSettingsChange,
                coroutineScope = scope
            )
        }
    }
}