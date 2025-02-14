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

    @Serializable
    private data class SavedState(
        val metadata: ArtworkMetadata,
        val lastUpdateTime: Long,
        val lastScheduledUpdateTime: Long
    )

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

    fun saveState(metadata: ArtworkMetadata) {
        try {
            stateFile.parent.createDirectories()
            val state = SavedState(metadata, lastUpdateTime, lastScheduledUpdateTime)
            stateFile.writeText(Json.encodeToString(state))
        } catch (e: Exception) {
            logger.error("Failed to save wallpaper state", e)
        }
    }

    fun start() {
        logger.info("Starting wallpaper manager")
        stop()
        
        loadSavedState()
        
        scope.launch {
            val now = LocalDateTime.now()
            val scheduledTime = LocalDateTime.of(
                now.toLocalDate(),
                LocalTime.of(settings.updateTimeHour, settings.updateTimeMinute)
            )
            
            // Check if we need to update immediately
            if (now.isAfter(scheduledTime) && 
                Duration.ofMillis(System.currentTimeMillis() - lastScheduledUpdateTime).toHours() >= settings.changeIntervalHours) {
                logger.info("Current time is after scheduled time and interval has passed, updating immediately")
                updateWallpaper()
            } else {
                logger.info("No immediate update needed - waiting for next scheduled time")
            }
            
            // Start the update loop
            job = scope.launch {
                while (isActive) {
                    val nextUpdateTime = calculateNextUpdateTime()
                    val delayMillis = nextUpdateTime - System.currentTimeMillis()
                    
                    if (delayMillis > 0) {
                        logger.info("Waiting ${Duration.ofMillis(delayMillis).toHours()} hours until next update")
                        delay(delayMillis)
                    }
                    
                    if (isActive) {
                        updateWallpaper()
                    }
                }
            }
        }
    }

    fun stop() {
        logger.info("Stopping wallpaper manager")
        job?.cancel()
        job = null
    }

    private suspend fun updateWallpaper() {
        if (!connectivityChecker.isNetworkAvailable()) {
            logger.info("No internet connection available, skipping update")
            return
        }

        val now = System.currentTimeMillis()
        val timeSinceLastScheduledUpdate = now - lastScheduledUpdateTime
        if (timeSinceLastScheduledUpdate < Duration.ofHours(settings.changeIntervalHours.toLong()).toMillis()) {
            logger.info("Skipping update - last scheduled update was ${Duration.ofMillis(timeSinceLastScheduledUpdate).toHours()} hours ago")
            return
        }

        try {
            artworkProvider.fetchArtwork()
                .onSuccess { (path, metadata) ->
                    val storedPath = artworkStorage.saveArtwork(path, metadata)
                    wallpaperService.setWallpaper(storedPath).onSuccess {
                        setScheduledWallpaper(storedPath, metadata)
                        historyManager.addToHistory(metadata)
                        artworkProvider.cleanupCacheFiles(metadata.id)
                    }
                }
        } catch (e: Exception) {
            logger.error("Failed to update wallpaper", e)
            throw e
        }
    }

    fun setCurrentArtwork(path: Path, metadata: ArtworkMetadata) {
        _currentArtwork.value = path to metadata
        lastSuccessfulArtwork = path to metadata
        scope.launch {
            saveState(metadata)
        }
        logger.info("Set current artwork: ${metadata.title}")
    }

    private fun calculateNextUpdateTime(): Long {
        val now = LocalDateTime.now()
        val hour = settings.updateTimeHour
        val minute = settings.updateTimeMinute
        
        var nextUpdate = LocalDateTime.of(
            now.toLocalDate(),
            LocalTime.of(hour, minute)
        )
        
        // If we're past today's update time, schedule for tomorrow
        if (now.isAfter(nextUpdate)) {
            nextUpdate = nextUpdate.plusDays(1)
            logger.info("Current time is after today's update time, scheduling for tomorrow")
        }
        
        val nextUpdateMillis = nextUpdate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        logger.info("Next update scheduled for: $nextUpdate (in ${Duration.ofMillis(nextUpdateMillis - System.currentTimeMillis()).toHours()} hours)")
        
        return nextUpdateMillis
    }

    fun setWallpaperManually(path: Path, metadata: ArtworkMetadata) {
        wallpaperService.setWallpaper(path)
        lastSuccessfulArtwork = path to metadata
        _currentArtwork.value = path to metadata
        lastUpdateTime = System.currentTimeMillis()
        saveState(metadata)
        logger.info("Wallpaper set manually: ${metadata.title}")
    }

    private fun setScheduledWallpaper(path: Path, metadata: ArtworkMetadata) {
        wallpaperService.setWallpaper(path)
        lastSuccessfulArtwork = path to metadata
        _currentArtwork.value = path to metadata
        lastScheduledUpdateTime = System.currentTimeMillis()
        lastUpdateTime = System.currentTimeMillis()
        saveState(metadata)
        logger.info("Wallpaper set through scheduled update: ${metadata.title}")
    }

    fun checkAndUpdate() {
        val now = LocalDateTime.now()
        val scheduledTime = LocalDateTime.of(
            now.toLocalDate(),
            LocalTime.of(settings.updateTimeHour, settings.updateTimeMinute)
        )
        
        if (now.isAfter(scheduledTime) && 
            Duration.ofMillis(System.currentTimeMillis() - lastScheduledUpdateTime).toHours() >= settings.changeIntervalHours) {
            logger.info("Periodic check: Update needed - last scheduled update was more than ${settings.changeIntervalHours} hours ago")
            scope.launch {
                updateWallpaper()
            }
        } else {
            logger.info("Periodic check: No update needed - last scheduled update was ${Duration.ofMillis(System.currentTimeMillis() - lastScheduledUpdateTime).toHours()} hours ago")
        }
    }
} 