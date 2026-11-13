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
    // Configure macOS agent (no Dock/Cmd+Tab) and template tray rendering before AWT loads.
    MacPlatform.configureDesktopEnvironment()
    WindowsPlatform.registerAppUserModelId("com.prince.artwallpaper")


    // Add command line argument handling
    if (args.contains("--version")) {
        println("Art Wallpaper v${AppInfo.version}")
        System.exit(0)
        return
    }

    if (args.contains("--smoke-test")) {
        println("Art Wallpaper v${AppInfo.version} runtime smoke test OK")
        System.exit(0)
        return
    }
    if (args.contains("--clear-data")) {
        DataCleanup.clearAllData()
        println("All application data cleared")
        System.exit(0)
        return
    }

    val singleInstanceManager = service.SingleInstanceManager()
    var wakeCallback: (() -> Unit)? = null

    if (!singleInstanceManager.acquireOrWake { wakeCallback?.invoke() }) {
        println("Art Wallpaper is already running. Signaled existing instance.")
        System.exit(0)
        return
    }

    try {
        // Clean up obsolete installer packages on startup
        // Fix #12 (Low): Defer installer cleanup to a background IO coroutine rather than blocking startup
        CoroutineScope(Dispatchers.IO).launch {
            try {
                service.UpdateManager.cleanupOldInstallers()
            } catch (_: Exception) {}
        }

        val settings = Settings.loadSavedSettings()
        val startMinimized = args.contains("--minimized") && !settings.isFirstRun
        val isAutoStart = args.contains("--autostart")

        // Don't start the service if it's first run or if we're not auto-starting
        val shouldStartService = !settings.isFirstRun && (isAutoStart || !startMinimized)

        // Initialize components
        val wallpaperService = WallpaperService.create()
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
            singleInstanceManager,
            wallpaperManager,
            onSetWakeCallback = { cb -> wakeCallback = cb }
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



private fun setupApplication(
    controller: ServiceController,
    settings: Settings,
    startMinimized: Boolean,
    shouldStartService: Boolean,
    singleInstanceManager: service.SingleInstanceManager,
    wallpaperManager: WallpaperManager,
    onSetWakeCallback: (() -> Unit) -> Unit
) {
    var windowVisibilityCallback: ((Boolean) -> Unit)? = null
    var updateDialogCallback: (() -> Unit)? = null
    var activeAwtWindow: java.awt.Window? = null
    val appBgScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val updateManager = service.UpdateManager()

    onSetWakeCallback {
        javax.swing.SwingUtilities.invokeLater {
            wallpaperManager.syncWithActiveDesktopWallpaper()
            controller.triggerCheckIfRunning()
            windowVisibilityCallback?.invoke(true)
            activeAwtWindow?.let { win ->
                WindowsPlatform.bringToFront(win)
            }
            if (MacPlatform.isMac) {
                MacPlatform.activateApp()
            }
        }
    }

    // Initialize system tray
    val systemTray = SystemTrayMenu(
        serviceController = controller,
        onShowWindow = {
            wallpaperManager.syncWithActiveDesktopWallpaper()
            controller.triggerCheckIfRunning()
            windowVisibilityCallback?.invoke(true)
            activeAwtWindow?.let { win ->
                win.toFront()
                WindowsPlatform.bringToFront(win)
            }
            if (MacPlatform.isMac) {
                MacPlatform.activateApp()
            }
            true
        },
        updateManager = updateManager,
        onOpenUpdateDialog = {
            windowVisibilityCallback?.invoke(true)
            updateDialogCallback?.invoke()
            activeAwtWindow?.let { win ->
                win.toFront()
                WindowsPlatform.bringToFront(win)
            }
            if (MacPlatform.isMac) {
                MacPlatform.activateApp()
            }
        }
    )
    systemTray.show()

    // Background polling for new GitHub releases; notifies once per newly discovered release
    val updatePollingJob = updateManager.startBackgroundPolling(
        scope = appBgScope,
        initialDelayMillis = 8_000L,
        isPollingEnabled = {
            val live = Settings.currentSettings.value
            !live.isFirstRun && live.autoCheckUpdates
        },
        onNewUpdate = { available ->
            val live = Settings.currentSettings.value
            if (live.dismissedUpdateVersion != available.latestVersion) {
                systemTray.displayUpdateNotification(available)
            }
        }
    )

    // Setup shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread {
        kotlinx.coroutines.runBlocking {
            try {
                updatePollingJob.cancel()
                appBgScope.cancel()
                controller.cleanup()
                singleInstanceManager.cleanup()
                logger.info("Application shutdown completed")
            } catch (e: Exception) {
                logger.error("Error during shutdown", e)
            }
        }
    })

    // Configure auto-start if not already enabled and migrate legacy macOS LaunchAgent plists
    if (!settings.hasEnabledAutoStart && !settings.isFirstRun) {
        WindowsAutoStart.enable()
        settings.copy(hasEnabledAutoStart = true).save()
    } else if (MacPlatform.isMac) {
        WindowsAutoStart.refreshIfNeeded()
    }

    // Fix #5 (Medium): Start service asynchronously on background dispatcher; do NOT block
    // the main AWT thread with runBlocking before application initialization.
    if (shouldStartService) {
        appBgScope.launch {
            controller.startService()
        }
    }

    application {
        var isWindowVisible by remember { mutableStateOf(!startMinimized) }
        var openUpdateDialogRequested by remember { mutableStateOf(false) }

        // Set the callback to control window visibility and update modal deep-links
        windowVisibilityCallback = { visible ->
            isWindowVisible = visible
        }
        updateDialogCallback = {
            openUpdateDialogRequested = true
        }

        val currentSettings by Settings.currentSettings.collectAsState()

        // ── Live system theme tracking & user preference ───────────────────
        val isSystemDark = rememberSystemIsDark()
        val isDark = when (currentSettings.themeMode) {
            AppTheme.SYSTEM -> isSystemDark
            AppTheme.LIGHT  -> false
            AppTheme.DARK   -> true
        }

        // Start service if not already running
        LaunchedEffect(Unit) {
            if (!controller.isServiceRunning.value) {
                controller.startService()
            }
        }

        Window(
            onCloseRequest = {
                isWindowVisible = false
                if (currentSettings.isFirstRun) {
                    val updated = currentSettings.copy(isFirstRun = false)
                    Settings.updateCurrentSettings(updated)
                    wallpaperManager.updateSettings(updated)
                }
            },
            title = "Art Wallpaper",
            state = rememberWindowState(
                width = 960.dp,
                height = 640.dp,
                isMinimized = startMinimized
            ),
            visible = isWindowVisible,
            icon = painterResource("icons/icon.png")
        ) {
            val appScope = rememberCoroutineScope()

            // ── Windows 11 / macOS: sync native title bar and foreground activation ─────
            LaunchedEffect(window, isWindowVisible) {
                activeAwtWindow = window
                if (isWindowVisible) {
                    launch(Dispatchers.IO) {
                        wallpaperManager.syncWithActiveDesktopWallpaper()
                    }
                    window.toFront()
                    WindowsPlatform.bringToFront(window)
                    if (MacPlatform.isMac) {
                        MacPlatform.activateApp()
                    }
                }
            }

            LaunchedEffect(isDark) {
                WindowsPlatform.applyDarkTitleBar(window, isDark)
                WindowsPlatform.applyRoundedCorners(window)
                systemTray.updateIconTheme(isDark)
            }

            App(
                wallpaperManager = wallpaperManager,
                serviceController = controller,
                settings = currentSettings,
                isDark = isDark,
                updateManager = updateManager,
                openUpdateDialog = openUpdateDialogRequested,
                onUpdateDialogConsumed = { openUpdateDialogRequested = false },
                onSettingsChange = { newSettings ->
                    val oldSettings = currentSettings
                    Settings.updateCurrentSettings(newSettings)
                    wallpaperManager.updateSettings(newSettings)
                    val scheduleChanged = oldSettings.updateTimeHour != newSettings.updateTimeHour ||
                            oldSettings.updateTimeMinute != newSettings.updateTimeMinute ||
                            oldSettings.changeIntervalHours != newSettings.changeIntervalHours
                    if (scheduleChanged && controller.isServiceRunning.value) {
                        controller.restartService()
                    } else if (!controller.isServiceRunning.value && !newSettings.isFirstRun) {
                        appScope.launch {
                            controller.startService()
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun App(
    wallpaperManager: WallpaperManager,
    serviceController: ServiceController,
    settings: Settings,
    isDark: Boolean = true,
    updateManager: service.UpdateManager? = null,
    openUpdateDialog: Boolean = false,
    onUpdateDialogConsumed: () -> Unit = {},
    onSettingsChange: (Settings) -> Unit
) {
    // ArtWallpaperTheme now receives the live OS dark/light state
    ArtWallpaperTheme(darkTheme = isDark) {
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
                coroutineScope = scope,
                updateManager = updateManager,
                openUpdateDialog = openUpdateDialog,
                onUpdateDialogConsumed = onUpdateDialogConsumed
            )
        }
    }
}
