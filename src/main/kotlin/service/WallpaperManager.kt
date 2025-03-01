package service

import ArtworkMetadata
import ArtworkProvider
import ArtworkStorageManager
import Settings
import WindowsWallpaperService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import exception.NetworkUnavailableException

class WallpaperManager(
    private val wallpaperService: WindowsWallpaperService,
    private val artworkProvider: ArtworkProvider,
    private val artworkStorage: ArtworkStorageManager,
    private val historyManager: HistoryManager,
    private val connectivityChecker: ConnectivityChecker = ConnectivityChecker(),
    private val settings: Settings
) {
    private val logger = LoggerFactory.getLogger(WallpaperManager::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null
    private val stateFile = Path.of(System.getProperty("user.home"), ".artwallpaper", "wallpaper_state.json")
    private var lastSuccessfulArtwork: Pair<Path, ArtworkMetadata>? = null
    private var lastUpdateTime: Long = 0
    private var lastScheduledUpdateTime: Long = 0
    private val _currentArtwork = MutableStateFlow<Pair<Path, ArtworkMetadata>?>(null)
    val currentArtwork: StateFlow<Pair<Path, ArtworkMetadata>?> = _currentArtwork.asStateFlow()
    private val updateLock = Object()
    private var isUpdating = false

    private val updateRetryPolicy = RetryPolicy(
        maxAttempts = 3,
        initialDelay = 1000L,
        maxDelay = 5000L,
        factor = 2.0
    )

    @Serializable
    private data class SavedState(
        val metadata: ArtworkMetadata,
        val lastUpdateTime: Long,
        val lastScheduledUpdateTime: Long
    )

    init {
        // Load last saved state
        try {
            if (stateFile.exists()) {
                val savedState = Json.decodeFromString<SavedState>(stateFile.readText())
                lastUpdateTime = savedState.lastUpdateTime
                lastScheduledUpdateTime = savedState.lastScheduledUpdateTime
                
                // Set current artwork from saved state
                val storedPath = artworkStorage.getArtworkPath(savedState.metadata.id)
                if (storedPath != null) {
                    if (storedPath.exists()) {
                        setCurrentArtwork(storedPath, savedState.metadata)
                        lastSuccessfulArtwork = storedPath to savedState.metadata
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to load wallpaper state", e)
        }
    }

    suspend fun checkAndUpdateIfNeeded() {
        val state = loadState()
        val now = System.currentTimeMillis()
        
        if (state == null || shouldUpdate(state.lastUpdateTime)) {
            logger.info("Update needed - last update was too long ago or no state found")
            updateWallpaper()
        } else {
            logger.info("No update needed - last update was ${Duration.ofMillis(now - state.lastUpdateTime).toHours()} hours ago")
        }
    }

    private fun shouldUpdate(lastUpdateTime: Long): Boolean {
        val now = System.currentTimeMillis()
        val hoursSinceLastUpdate = Duration.ofMillis(now - lastUpdateTime).toHours()
        return hoursSinceLastUpdate >= settings.updateInterval.toHours()
    }

    private fun calculateNextUpdateTime(): LocalDateTime {
        val now = LocalDateTime.now()
        val nextUpdate = now.plus(settings.updateInterval)
        
        // If we have preferred hours, adjust to next available time
        return settings.preferredHours.let { hours ->
            val todayPreferred = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hours, 0))
            if (now.isBefore(todayPreferred)) {
                todayPreferred
            } else {
                todayPreferred.plusDays(1)
            }
        } ?: nextUpdate
    }

    private suspend fun updateWallpaper(forceUpdate: Boolean = false) {
        synchronized(updateLock) {
            if (isUpdating) {
                logger.info("Update already in progress, skipping")
                return
            }
            isUpdating = true
        }
        
        try {
            updateRetryPolicy.execute {
                if (!connectivityChecker.isNetworkAvailable()) {
                    throw NetworkUnavailableException()
                }
                
                val artwork = artworkProvider.fetchArtwork().getOrThrow()
                val storedPath = artworkStorage.saveArtwork(artwork.first, artwork.second)
                wallpaperService.setWallpaper(storedPath).getOrThrow()
                setScheduledWallpaper(storedPath, artwork.second)
                artworkProvider.cleanupCacheFiles()
            }
        } catch (e: Exception) {
            logger.error("Failed to update wallpaper after retries", e)
            throw e
        } finally {
            synchronized(updateLock) {
                isUpdating = false
            }
        }
    }

    private fun loadState(): SavedState? {
        return try {
            if (stateFile.exists()) {
                Json.decodeFromString(stateFile.readText())
            } else null
        } catch (e: Exception) {
            logger.error("Failed to load state", e)
            null
        }
    }

    private fun saveState(metadata: ArtworkMetadata) {
        try {
            stateFile.parent.createDirectories()
            val state = SavedState(
                metadata = metadata,
                lastUpdateTime = lastUpdateTime,
                lastScheduledUpdateTime = lastScheduledUpdateTime
            )
            stateFile.writeText(Json.encodeToString(state))
            logger.info("Saved state for artwork: ${metadata.title}")
        } catch (e: Exception) {
            logger.error("Failed to save wallpaper state", e)
        }
    }

    suspend fun start() {
        synchronized(updateLock) {
            if (job != null) {
                logger.info("Wallpaper manager already running")
                return
            }
            
            logger.info("Starting wallpaper manager")
            job = scope.launch {
                try {
                    // Only check for updates if not first run
                    if (!settings.isFirstRun) {
                        checkAndUpdateIfNeeded()
                    }
                } catch (e: Exception) {
                    logger.error("Error during wallpaper update check", e)
                }
            }
        }
    }

    fun stop() {
        logger.info("Stopping wallpaper manager")
        job?.cancel()
        job = null
    }

    private fun setCurrentArtwork(path: Path, metadata: ArtworkMetadata) {
        _currentArtwork.value = path to metadata
        lastSuccessfulArtwork = path to metadata
        scope.launch {
            saveState(metadata)
        }
        logger.info("Set current artwork: ${metadata.title}")
    }

    private fun setScheduledWallpaper(path: Path, metadata: ArtworkMetadata) {
        wallpaperService.setWallpaper(path).onSuccess {
            lastSuccessfulArtwork = path to metadata
            _currentArtwork.value = path to metadata
            lastScheduledUpdateTime = System.currentTimeMillis()
            lastUpdateTime = System.currentTimeMillis()
            saveState(metadata)
            artworkProvider.cleanupCacheFiles(metadata.id)
            logger.info("Wallpaper set through scheduled update: ${metadata.title}")
        }
    }

    fun checkAndUpdate() {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val scheduledTime = LocalDateTime.of(
            today,
            LocalTime.of(settings.updateTimeHour, settings.updateTimeMinute)
        )
        
        val lastUpdateDate = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(lastScheduledUpdateTime),
            ZoneId.systemDefault()
        ).toLocalDate()
        
        if ((now.isAfter(scheduledTime) || now.isEqual(scheduledTime)) && 
            !today.isEqual(lastUpdateDate)) {
            logger.info("Update needed - haven't updated today and we're at/past scheduled time $scheduledTime")
            scope.launch {
                updateWallpaper()
            }
        } else {
            val nextUpdate = if (now.isBefore(scheduledTime)) {
                scheduledTime
            } else {
                scheduledTime.plusDays(1)
            }
            logger.info("No update needed - next update scheduled for $nextUpdate")
        }
    }

    private fun loadSavedState() {
        try {
            if (stateFile.exists()) {
                val state = Json.decodeFromString<SavedState>(stateFile.readText())
                val imagePath = artworkStorage.getArtworkPath(state.metadata.id)
                if (imagePath != null) {
                    setCurrentArtwork(imagePath, state.metadata)
                    lastUpdateTime = state.lastUpdateTime
                    lastScheduledUpdateTime = state.lastScheduledUpdateTime
                    logger.info("Loaded saved state - Last scheduled update: ${state.lastScheduledUpdateTime}")
                }
            } else {
                logger.info("No saved wallpaper state found")
                lastUpdateTime = 0
                lastScheduledUpdateTime = 0
            }
        } catch (e: Exception) {
            logger.error("Failed to load saved wallpaper state", e)
            lastUpdateTime = 0
            lastScheduledUpdateTime = 0
        }
    }

    fun setWallpaperManually(path: Path, metadata: ArtworkMetadata) {
        wallpaperService.setWallpaper(path).onSuccess {
            setCurrentArtwork(path, metadata)
            lastUpdateTime = System.currentTimeMillis()
            artworkProvider.cleanupCacheFiles()
            logger.info("Wallpaper set manually: ${metadata.title}")
        }
    }

    suspend fun setNextWallpaperManually() {
        try {
            val artwork = artworkProvider.fetchArtwork().getOrThrow()
            val storedPath = artworkStorage.saveArtwork(artwork.first, artwork.second)
            setWallpaperManually(storedPath, artwork.second)
            
            artworkStorage.cleanupOldArtworks()
        } catch (e: Exception) {
            logger.error("Failed to set next wallpaper manually", e)
            throw e
        }
    }
}

class RetryPolicy(
    private val maxAttempts: Int,
    private val initialDelay: Long,
    private val maxDelay: Long,
    private val factor: Double
) {
    suspend fun <T> execute(block: suspend () -> T): T {
        var currentDelay = initialDelay
        repeat(maxAttempts - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
                delay(currentDelay)
            }
        }
        return block() // Last attempt
    }
} 