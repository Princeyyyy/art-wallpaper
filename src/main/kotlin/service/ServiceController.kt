import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.Duration

class ServiceController(
    private val wallpaperManager: WallpaperManager,
    private val artworkStorage: ArtworkStorageManager,
    private val historyManager: HistoryManager,
    private val connectivityChecker: ConnectivityChecker,
    private val artworkProvider: ArtworkProvider
) {
    private val logger = LoggerFactory.getLogger(ServiceController::class.java)
    var isServiceRunning = false
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var cleanupJob: Job? = null

    fun startService() {
        if (!isServiceRunning) {
            wallpaperManager.start()
            startCleanupJob()
            isServiceRunning = true
        }
    }

    fun stopService() {
        if (isServiceRunning) {
            scope.cancel()
            wallpaperManager.stop()
            cleanupJob?.cancel()
            isServiceRunning = false
        }
    }

    fun restartService() {
        stopService()
        startService()
    }

    suspend fun previousWallpaper() {
        // Get the last wallpaper from history
        val history = historyManager.getHistory()
        if (history.size > 1) {
            val previousArtwork = history[1] // Get second item (previous wallpaper)
            artworkStorage.getStoredArtworks()
                .find { it.second.id == previousArtwork.id }
                ?.let { (path, metadata) ->
                    wallpaperManager.setWallpaper(path, metadata)
                }
        }
    }

    suspend fun nextWallpaper() {
        var attempts = 0
        val maxAttempts = 3
        val backoffDelay = 5000L
        
        while (attempts < maxAttempts) {
            try {
                if (!connectivityChecker.isNetworkAvailable()) {
                    logger.info("No internet connection available for next wallpaper")
                    throw IllegalStateException("No internet connection available")
                }

                artworkProvider.fetchArtwork()
                    .onSuccess { (path, metadata) ->
                        val storedPath = artworkStorage.saveArtwork(path, metadata)
                        wallpaperManager.setWallpaper(storedPath, metadata)
                        wallpaperManager.setCurrentArtwork(storedPath, metadata)
                        historyManager.addToHistory(metadata)
                        artworkProvider.cleanupCacheFiles(metadata.id)
                        // Save state
                        wallpaperManager.saveState(metadata)
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
            while (isActive) {
                try {
                    // Run cleanup every 24 hours
                    delay(Duration.ofHours(24).toMillis())
                    logger.info("Starting scheduled cleanup")
                    artworkStorage.cleanupStorage()
                    historyManager.cleanupHistory(50) // Keep last 50 entries
                } catch (e: Exception) {
                    logger.error("Failed to run cleanup", e)
                    delay(Duration.ofMinutes(30).toMillis())
                }
            }
        }
    }
} 