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
    private val _currentArtwork = MutableStateFlow<Pair<Path, ArtworkMetadata>?>(null)
    val currentArtwork: StateFlow<Pair<Path, ArtworkMetadata>?> = _currentArtwork.asStateFlow()

    @Serializable
    private data class SavedState(
        val metadata: ArtworkMetadata,
        val lastUpdateTime: Long
    )

    init {
        // Remove the init block loading since we handle it in start()
    }

    private fun loadSavedState() {
        try {
            if (stateFile.exists()) {
                val state = Json.decodeFromString<SavedState>(stateFile.readText())
                val imagePath = artworkStorage.getArtworkPath(state.metadata.id)
                if (imagePath != null) {
                    setCurrentArtwork(imagePath, state.metadata)
                    logger.info("Loaded saved wallpaper state: ${state.metadata.title}")
                    
                    // Update lastUpdateTime to current time since we're loading a saved state
                    lastUpdateTime = System.currentTimeMillis()
                    saveState(state.metadata)
                    
                    logger.info("Updated last update time to now")
                }
            } else {
                logger.info("No saved wallpaper state found")
            }
        } catch (e: Exception) {
            logger.error("Failed to load saved wallpaper state", e)
        }
    }

    fun saveState(metadata: ArtworkMetadata) {
        try {
            stateFile.parent.createDirectories()
            val state = SavedState(metadata, lastUpdateTime)
            stateFile.writeText(Json.encodeToString(state))
        } catch (e: Exception) {
            logger.error("Failed to save wallpaper state", e)
        }
    }

    fun start() {
        logger.info("Starting wallpaper manager")
        stop()
        
        // Load saved state first
        loadSavedState()
        
        job = scope.launch {
            while (isActive) {
                val nextUpdateTime = calculateNextUpdateTime()
                val delayMillis = nextUpdateTime - System.currentTimeMillis()
                
                if (delayMillis > 0) {
                    delay(delayMillis)
                }
                
                if (isActive) {
                    updateWallpaper()
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

        val timeSinceLastUpdate = System.currentTimeMillis() - lastUpdateTime
        if (timeSinceLastUpdate < Duration.ofHours(settings.changeIntervalHours.toLong()).toMillis()) {
            logger.info("Skipping update - last update was ${Duration.ofMillis(timeSinceLastUpdate).toHours()} hours ago")
            return
        }

        try {
            artworkProvider.fetchArtwork()
                .onSuccess { (path, metadata) ->
                    val storedPath = artworkStorage.saveArtwork(path, metadata)
                    wallpaperService.setWallpaper(storedPath).onSuccess {
                        _currentArtwork.value = storedPath to metadata
                        lastSuccessfulArtwork = storedPath to metadata
                        lastUpdateTime = System.currentTimeMillis()
                        historyManager.addToHistory(metadata)
                        saveState(metadata)
                        artworkProvider.cleanupCacheFiles(metadata.id)
                        logger.info("Successfully updated wallpaper: ${metadata.title}")
                    }
                }
        } catch (e: Exception) {
            logger.error("Failed to update wallpaper", e)
            throw e
        }
    }

    suspend fun setWallpaper(path: Path, metadata: ArtworkMetadata) {
        wallpaperService.setWallpaper(path)
        lastSuccessfulArtwork = path to metadata
        lastUpdateTime = System.currentTimeMillis()
        _currentArtwork.value = path to metadata
        saveState(metadata)
        logger.info("Wallpaper set manually: ${metadata.title}")
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
        
        val hour = if (!settings.hasSetUpdateTime) 7 else settings.updateTimeHour
        val minute = if (!settings.hasSetUpdateTime) 0 else settings.updateTimeMinute
        
        var nextUpdate = LocalDateTime.of(
            now.toLocalDate(),
            LocalTime.of(hour, minute)
        )
        
        // If today's update time has passed, schedule for tomorrow
        if (now.isAfter(nextUpdate)) {
            nextUpdate = nextUpdate.plusDays(1)
        }
        
        val nextUpdateMillis = nextUpdate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        logger.info("Next update scheduled for: ${nextUpdate}")
        
        return nextUpdateMillis
    }
} 