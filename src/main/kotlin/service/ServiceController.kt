package service

import ArtworkMetadata
import ArtworkProvider
import ArtworkStorageManager
import WallpaperFitMode
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.awt.Desktop
import java.awt.desktop.SystemSleepEvent
import java.awt.desktop.SystemSleepListener

class ServiceController(
    val wallpaperManager: WallpaperManager,
    private val artworkStorage: ArtworkStorageManager,
    private val historyManager: HistoryManager,
    private val artworkProvider: ArtworkProvider
) {
    private val logger = LoggerFactory.getLogger(ServiceController::class.java)
    private val _isServiceRunning = MutableStateFlow(false)
    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var cleanupJob: Job? = null
    private var checkJob: Job? = null
    private val isRestarting = AtomicBoolean(false)
    private val restartScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var restartDebounceJob: Job? = null
    private val lastCheckTime = AtomicLong(0)
    private var sleepListener: SystemSleepListener? = null

    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    // Wake on a lightweight 1-minute wall-clock tick so OS sleep / macOS App Nap
    // never stalls the 30-minute check window after the machine wakes up.
    private fun startPeriodicCheck() {
        checkJob?.cancel()
        checkJob = serviceScope.launch {
            val checkInterval = Duration.ofMinutes(30).toMillis()
            val tickInterval = Duration.ofMinutes(1).toMillis()
            while (isActive) {
                val now = System.currentTimeMillis()
                val lastCheck = lastCheckTime.get()
                val elapsed = now - lastCheck

                if (lastCheck == 0L || elapsed >= checkInterval) {
                    try {
                        logger.info("Running periodic check for wallpaper update")
                        wallpaperManager.checkAndUpdate()
                        lastCheckTime.set(System.currentTimeMillis())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.error("Error during periodic wallpaper check", e)
                    }
                }
                delay(tickInterval)
            }
        }
    }

    fun triggerCheckIfRunning() {
        if (!_isServiceRunning.value) return
        serviceScope.launch {
            try {
                logger.info("Triggering wallpaper schedule check on wake/activation")
                wallpaperManager.checkAndUpdate()
                lastCheckTime.set(System.currentTimeMillis())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Error during wake wallpaper check", e)
            }
        }
    }

    private fun registerSleepListener() {
        if (sleepListener != null) return
        try {
            if (!Desktop.isDesktopSupported()) return
            val desktop = Desktop.getDesktop()
            val listener = object : SystemSleepListener {
                override fun systemAboutToSleep(e: SystemSleepEvent?) {}
                override fun systemAwoke(e: SystemSleepEvent?) {
                    logger.info("System woke from sleep, checking wallpaper schedule")
                    triggerCheckIfRunning()
                }
            }
            desktop.addAppEventListener(listener)
            sleepListener = listener
        } catch (e: Throwable) {
            logger.debug("SystemSleepListener not supported on this platform/environment: ${e.message}")
        }
    }

    private fun unregisterSleepListener() {
        val listener = sleepListener ?: return
        sleepListener = null
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().removeAppEventListener(listener)
            }
        } catch (_: Throwable) {
        }
    }

    suspend fun startService() {
        if (Settings.currentSettings.value.isFirstRun) {
            logger.info("First run active, skipping background service start")
            return
        }

        mutex.withLock {
            if (_isServiceRunning.value) {
                logger.info("Service already running")
                return
            }

            wallpaperManager.start()
            _isServiceRunning.value = true
            lastCheckTime.set(System.currentTimeMillis())
            logger.info("Service started successfully")

            startPeriodicCheck()
            startCleanupJob()
            registerSleepListener()
        }
    }

    fun stopService() {
        serviceScope.launch {
            mutex.withLock {
                if (_isServiceRunning.value) {
                    try {
                        unregisterSleepListener()
                        wallpaperManager.stop()
                        cleanupJob?.cancel()
                        checkJob?.cancel()
                        _isServiceRunning.value = false
                        logger.info("Service stopped successfully")
                    } catch (e: Exception) {
                        logger.error("Error stopping service", e)
                    }
                }
            }
        }
    }

    fun restartService() {
        if (!isRestarting.compareAndSet(false, true)) return

        restartDebounceJob?.cancel()
        restartDebounceJob = restartScope.launch {
            try {
                delay(300) // Debounce rapid settings changes
                if (_isServiceRunning.value) {
                    mutex.withLock {
                        wallpaperManager.stop()
                        cleanupJob?.cancel()
                        checkJob?.cancel()
                        _isServiceRunning.value = false
                    }
                    delay(150)
                    serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                    startService()
                }
            } finally {
                isRestarting.set(false)
            }
        }
    }

    suspend fun nextWallpaper() {
        if (isServiceRunning.value) {
            wallpaperManager.setNextWallpaperManually()
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val artwork = artworkProvider.fetchArtwork().getOrThrow()
                val storedPath = artworkStorage.saveArtwork(artwork.first, artwork.second)
                wallpaperManager.setWallpaperManually(storedPath, artwork.second)
                artworkStorage.cleanupOldArtworks()
            } catch (e: Exception) {
                logger.warn("Could not set next wallpaper: ${e.message}")
                throw e
            }
        }
    }

    suspend fun reprocessWallpaper(fitMode: WallpaperFitMode) {
        wallpaperManager.reprocessCurrentWallpaper(fitMode)
    }

    fun getStoredArtworks(): List<Pair<Path, ArtworkMetadata>> {
        return artworkStorage.getStoredArtworks()
    }

    suspend fun selectStoredArtwork(path: Path, metadata: ArtworkMetadata) {
        withContext(Dispatchers.IO) {
            wallpaperManager.setWallpaperManually(path, metadata)
        }
    }

    private fun startCleanupJob() {
        cleanupJob?.cancel()
        cleanupJob = serviceScope.launch {
            val cleanupInterval = Duration.ofHours(24).toMillis()
            while (isActive) {
                delay(cleanupInterval)
                try {
                    withContext(Dispatchers.IO) {
                        artworkStorage.cleanupOldArtworks()
                        artworkStorage.cleanup()
                    }
                } catch (e: Exception) {
                    logger.error("Error during scheduled artwork cleanup", e)
                }
            }
        }
    }

    suspend fun cleanup() {
        try {
            unregisterSleepListener()
            if (_isServiceRunning.value) {
                wallpaperManager.stop()
                cleanupJob?.cancel()
                checkJob?.cancel()
                _isServiceRunning.value = false
            }
            withContext(Dispatchers.IO) {
                artworkStorage.cleanup()
            }
        } catch (e: Exception) {
            logger.error("Error during service cleanup", e)
        } finally {
            serviceScope.cancel()
            restartScope.cancel()
            logger.info("Service cleanup completed")
        }
    }
}
