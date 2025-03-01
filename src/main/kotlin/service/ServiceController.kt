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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ServiceController(
    private val wallpaperManager: WallpaperManager,
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
    private var isRestarting = false
    private val restartScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var restartDebounceJob: Job? = null
    private val lastCheckTime = AtomicLong(0)

    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private fun startPeriodicCheck() {
        checkJob?.cancel()
        checkJob = serviceScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val lastCheck = lastCheckTime.get()
                
                if (now - lastCheck >= Duration.ofMinutes(30).toMillis()) {
                    logger.info("Performing periodic check for wallpaper updates")
                    try {
                        wallpaperManager.checkAndUpdate()
                        lastCheckTime.set(now)
                    } catch (e: Exception) {
                        logger.error("Error during periodic check", e)
                    }
                }
                delay(Duration.ofMinutes(5).toMillis())
            }
        }
    }

    private suspend fun safeServiceOperation(operation: suspend () -> Unit) {
        val maxRetries = 3
        var attempts = 0
        var lastException: Exception? = null
        
        while (attempts < maxRetries) {
            try {
                operation()
                return
            } catch (e: Exception) {
                lastException = e
                logger.error("Operation failed (attempt ${attempts + 1}/$maxRetries)", e)
                attempts++
                if (attempts < maxRetries) {
                    delay(Duration.ofSeconds((5 * attempts).toLong()).toMillis())
                }
            }
        }
        
        throw lastException ?: RuntimeException("Operation failed after $maxRetries attempts")
    }

    fun startService(isAutoStart: Boolean = false) {
        serviceScope.launch {
            mutex.withLock {
                if (!_isServiceRunning.value) {
                    try {
                        safeServiceOperation {
                            wallpaperManager.start()
                            startCleanupJob()
                            startPeriodicCheck()
                            _isServiceRunning.value = true
                            logger.info("Service started${if (isAutoStart) " (auto-start)" else ""}")
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to start service", e)
                        _isServiceRunning.value = false
                        throw e
                    }
                }
            }
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
                        logger.info("Service stopped")
                    } catch (e: Exception) {
                        logger.error("Error stopping service", e)
                        throw e
                    }
                }
            }
        }
    }

    fun restartService() {
        if (isRestarting) return
        
        restartDebounceJob?.cancel()
        restartDebounceJob = restartScope.launch {
            delay(500) // Debounce multiple calls
            isRestarting = true
            try {
                if (_isServiceRunning.value) {
                    stopService()
                    delay(500)
                    serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                    startService()
                    delay(100)
                    if (!_isServiceRunning.value) {
                        logger.error("Service failed to restart")
                    }
                }
            } finally {
                isRestarting = false
            }
        }
    }

    suspend fun nextWallpaper() {
        val maxAttempts = 3
        val backoffDelay = 1000L
        var attempts = 0

        while (attempts < maxAttempts) {
            try {
                artworkProvider.fetchArtwork()
                    .onSuccess { (path, metadata) ->
                        val storedPath = artworkStorage.saveArtwork(path, metadata)
                        wallpaperManager.setWallpaperManually(storedPath, metadata)
                        historyManager.addToHistory(metadata)
                        artworkProvider.cleanupCacheFiles(metadata.id)
                        logger.info("Successfully set next wallpaper: ${metadata.title}")
                        return
                    }
                attempts++
                delay(backoffDelay * attempts)
            } catch (e: Exception) {
                logger.error("Attempt $attempts failed", e)
                if (attempts >= maxAttempts) {
                    throw IllegalStateException("Failed to update wallpaper after $maxAttempts attempts")
                }
                delay(backoffDelay * attempts)
            }
        }
    }

    private fun startCleanupJob() {
        cleanupJob?.cancel()
        cleanupJob = serviceScope.launch {
            while (isActive) {
                try {
                    artworkStorage.cleanupOldArtworks()
                } catch (e: Exception) {
                    logger.error("Error during artwork cleanup", e)
                }
                delay(Duration.ofHours(24).toMillis())
            }
        }
    }

    fun cleanup() {
        serviceScope.launch {
            mutex.withLock {
                try {
                    stopService()
                    artworkStorage.cleanup()
                    serviceScope.cancel()
                    restartScope.cancel()
                } catch (e: Exception) {
                    logger.error("Error during cleanup", e)
                }
            }
        }
    }
} 