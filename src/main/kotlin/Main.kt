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
private var serviceController: ServiceController? = null

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
        val settings = Settings.load()
        val startMinimized = args.contains("--minimized") && !settings.isFirstRun
        val isAutoStart = args.contains("--autostart")
        
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
        
        serviceController = ServiceController(
            wallpaperManager = wallpaperManager,
            artworkStorage = artworkStorage,
            historyManager = historyManager,
            artworkProvider = artworkProvider
        )

        // Create a local immutable copy
        val controller = serviceController ?: run {
            logger.error("Failed to initialize ServiceController")
            System.exit(1)
            return
        }

        // Create a single shutdown hook handler
        setupShutdownHook(controller, lockFile)

        var windowVisibilityCallback: ((Boolean) -> Unit)? = null
        
        // Initialize system tray with non-null controller
        val systemTray = SystemTrayMenu(
            serviceController = controller,
            onShowWindow = { 
                windowVisibilityCallback?.invoke(true)
                true
            }
        )
        systemTray.show()

        // If auto-starting, start service and keep running in background
        if (isAutoStart) {
            controller.startService(isAutoStart = true)
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            
            Runtime.getRuntime().addShutdownHook(Thread {
                runBlocking {
                    controller.stopService()
                    scope.cancel()
                }
            })
            
            runBlocking {
                while (true) {
                    delay(Long.MAX_VALUE)
                }
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
    } catch (e: Exception) {
        logger.error("Fatal error during application startup", e)
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
private fun setupShutdownHook(controller: ServiceController, lockFile: Path, scope: CoroutineScope? = null) {
    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking {
            try {
                controller.cleanup()
                scope?.cancel()
                lockFile.deleteIfExists()
                logger.info("Application shutdown completed")
            } catch (e: Exception) {
                logger.error("Error during shutdown", e)
            }
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