package service

import ArtworkProvider
import ArtworkStorageManager
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    // Battery & CPU optimization: Sleep precisely until the next 30-minute interval
    // rather than constantly polling every 5 minutes and waking CPU cores.
    private fun startPeriodicCheck() {
        checkJob?.cancel()
        checkJob = serviceScope.launch {
            val checkInterval = Duration.ofMinutes(30).toMillis()
            while (isActive) {
                val now = System.currentTimeMillis()
                val lastCheck = lastCheckTime.get()
                val elapsed = now - lastCheck

                if (lastCheck == 0L || elapsed >= checkInterval) {
                    try {
                        logger.info("Running periodic check for wallpaper update")
                        wallpaperManager.checkAndUpdate()
                        lastCheckTime.set(System.currentTimeMillis())
                    } catch (e: Exception) {
                        logger.error("Error during periodic wallpaper check", e)
                    }
                    delay(checkInterval)
                } else {
                    val waitTime = (checkInterval - elapsed).coerceAtLeast(10_000L)
                    delay(waitTime)
                }
            }
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
            logger.info("Service started successfully")

            serviceScope.launch {
                try {
                    wallpaperManager.checkAndUpdateIfNeeded()
                } catch (e: Exception) {
                    logger.error("Error during initial wallpaper update check", e)
                }
            }
            startPeriodicCheck()
            startCleanupJob()
        }
    }

    fun stopService() {
        serviceScope.launch {
            mutex.withLock {
                if (_isServiceRunning.value) {
                    try {
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
                logger.error("Failed to set next wallpaper", e)
                throw e
            }
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
