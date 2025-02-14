package service

import ArtworkProvider
import ArtworkStorageManager
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ServiceController(
    private val wallpaperManager: WallpaperManager,
    private val artworkStorage: ArtworkStorageManager,
    private val historyManager: HistoryManager,
    private val artworkProvider: ArtworkProvider
) {
    private val logger = LoggerFactory.getLogger(ServiceController::class.java)
    private val _isServiceRunning = MutableStateFlow(false)
    private var scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var cleanupJob: Job? = null
    private var checkJob: Job? = null
    private var isRestarting = false
    private val restartScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var restartDebounceJob: Job? = null

    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private fun startPeriodicCheck() {
        checkJob?.cancel()
        checkJob = scope.launch {
            while (isActive) {
                logger.info("Performing hourly check for wallpaper updates")
                try {
                    wallpaperManager.checkAndUpdate()
                } catch (e: Exception) {
                    logger.error("Error during periodic check", e)
                }
                delay(Duration.ofHours(1).toMillis())
            }
        }
    }

    fun startService(isAutoStart: Boolean = false) {
        if (!_isServiceRunning.value) {
            wallpaperManager.start()
            startCleanupJob()
            startPeriodicCheck()
            _isServiceRunning.value = true
            logger.info("Service started${if (isAutoStart) " (auto-start)" else ""}")
        }
    }

    fun stopService() {
        if (_isServiceRunning.value) {
            wallpaperManager.stop()
            cleanupJob?.cancel()
            checkJob?.cancel()
            scope.cancel()
            _isServiceRunning.value = false
            logger.info("Service stopped")
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
                    scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
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
        cleanupJob = scope.launch {
            try {
                while (isActive) {
                    delay(Duration.ofHours(24).toMillis())
                    logger.info("Starting scheduled cleanup")
                    try {
                        artworkStorage.cleanupStorage()
                        historyManager.cleanupHistory(50)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        logger.error("Failed to run cleanup", e)
                        delay(Duration.ofMinutes(30).toMillis())
                    }
                }
            } catch (e: CancellationException) {
                logger.info("Cleanup job cancelled")
                throw e
            } catch (e: Exception) {
                logger.error("Cleanup job failed", e)
            }
        }
    }
} 