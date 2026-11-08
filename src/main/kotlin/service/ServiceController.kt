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
    // FIX #9: Use AtomicBoolean for isRestarting — plain Boolean read/write from
    // multiple coroutines without synchronisation is a data race.
    private val isRestarting = AtomicBoolean(false)
    private val restartScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var restartDebounceJob: Job? = null
    private val lastCheckTime = AtomicLong(0)

    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    // FIX #8: startPeriodicCheck() was defined but never called. It is now invoked
    // by startService() so scheduled wallpaper checks actually fire at runtime.
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

    suspend fun startService() {
        if (Settings.currentSettings.value.isFirstRun) {
            logger.info("First run detected, skipping service start")
            return
        }

        mutex.withLock {
            if (_isServiceRunning.value) {
                logger.info("Service already running")
                return
            }

            wallpaperManager.start()
            _isServiceRunning.value = true
            logger.info("Service started")

            // FIX #8: Do an immediate check on startup, then start the periodic loop.
            // Previously only the one-shot check ran and startPeriodicCheck() was never called.
            serviceScope.launch {
                try {
                    logger.info("Performing initial check for wallpaper updates")
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
        // FIX #9: compareAndSet is atomic — no race between the read and write.
        if (!isRestarting.compareAndSet(false, true)) return

        restartDebounceJob?.cancel()
        restartDebounceJob = restartScope.launch {
            try {
                delay(500) // Debounce multiple rapid calls
                if (_isServiceRunning.value) {
                    // Stop service and wait for it to actually stop before restarting.
                    mutex.withLock {
                        wallpaperManager.stop()
                        cleanupJob?.cancel()
                        checkJob?.cancel()
                        _isServiceRunning.value = false
                    }
                    delay(300)
                    // Fresh scope for the new service lifecycle.
                    serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                    startService()
                    delay(100)
                    if (!_isServiceRunning.value) {
                        logger.error("Service failed to restart")
                    }
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

        serviceScope.launch {
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

    // FIX #10: cleanup() previously launched work onto serviceScope and then cancelled
    // that same scope, so the inner work could be killed before completing.
    // Now we do all cleanup directly in the caller's scope (the shutdown hook calls
    // this via runBlocking, so it has its own context).
    suspend fun cleanup() {
        try {
            // Stop service state synchronously before cancelling scopes.
            if (_isServiceRunning.value) {
                wallpaperManager.stop()
                cleanupJob?.cancel()
                checkJob?.cancel()
                _isServiceRunning.value = false
            }
            artworkStorage.cleanup()
        } catch (e: Exception) {
            logger.error("Error during service cleanup", e)
        } finally {
            serviceScope.cancel()
            restartScope.cancel()
            logger.info("Service scopes cancelled")
        }
    }
}
