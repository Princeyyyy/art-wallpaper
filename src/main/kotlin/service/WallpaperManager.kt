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
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class WallpaperManager(
    private val wallpaperService: WindowsWallpaperService,
    private val artworkProvider: ArtworkProvider,
    private val artworkStorage: ArtworkStorageManager,
    private val historyManager: HistoryManager,
    private val connectivityChecker: ConnectivityChecker = ConnectivityChecker()
) {
    private val logger = LoggerFactory.getLogger(WallpaperManager::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null
    private val stateFile = Path.of(System.getProperty("user.home"), ".artwallpaper", "current_wallpaper.json")
    private var lastSuccessfulArtwork: Pair<Path, ArtworkMetadata>? = null
    private var lastUpdateTime: Long = 0
    private val _currentArtwork = MutableStateFlow<Pair<Path, ArtworkMetadata>?>(null)
    val currentArtwork: StateFlow<Pair<Path, ArtworkMetadata>?> = _currentArtwork.asStateFlow()
    val changeInterval: Duration = Duration.ofHours(24)  // Fixed 24-hour interval

    @Serializable
    private data class SavedState(
        val metadata: ArtworkMetadata,
        val lastUpdateTime: Long
    )

    init {
        // Load the last saved state
        scope.launch {
            loadSavedState()
        }
    }

    private fun loadSavedState() {
        try {
            if (stateFile.exists()) {
                val state = Json.decodeFromString<SavedState>(stateFile.readText())
                lastUpdateTime = state.lastUpdateTime
                val imagePath = artworkStorage.getArtworkPath(state.metadata.id)
                if (imagePath != null) {
                    setCurrentArtwork(imagePath, state.metadata)
                    logger.info("Loaded saved wallpaper state: ${state.metadata.title}")
                    
                    // Check if we need to update based on time
                    val timeSinceLastUpdate = System.currentTimeMillis() - lastUpdateTime
                    if (timeSinceLastUpdate < changeInterval.toMillis()) {
                        logger.info("Skipping update - last update was ${Duration.ofMillis(timeSinceLastUpdate).toHours()} hours ago")
                        return
                    }
                }
            }
            logger.info("No saved wallpaper state found or update needed")
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
        job?.cancel()
        
        job = scope.launch {
            // First try to load stored artwork
            val storedArtworks = artworkStorage.getStoredArtworks()
            if (storedArtworks.isNotEmpty()) {
                val lastArtwork = storedArtworks.last()
                setCurrentArtwork(lastArtwork.first, lastArtwork.second)
                logger.info("Loaded last saved artwork: ${lastArtwork.second.title}")
            } else {
                // If no stored artwork, fetch new one
                logger.info("No stored artwork found, fetching new artwork...")
                updateWallpaper()
            }
            
            // Start the periodic update cycle
            while (isActive) {
                try {
                    delay(changeInterval.toMillis())
                    updateWallpaper()
                } catch (e: Exception) {
                    logger.error("Failed to update wallpaper", e)
                    delay(Duration.ofMinutes(5).toMillis())
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
        // Check connectivity first
        if (!connectivityChecker.isNetworkAvailable()) {
            logger.info("No internet connection available, skipping update")
            if (lastSuccessfulArtwork != null) {
                logger.info("Using last successful artwork")
                _currentArtwork.value = lastSuccessfulArtwork
            }
            return
        }

        // Check if enough time has passed since last update
        val timeSinceLastUpdate = System.currentTimeMillis() - lastUpdateTime
        if (timeSinceLastUpdate < changeInterval.toMillis()) {
            logger.info("Skipping update - last update was ${Duration.ofMillis(timeSinceLastUpdate).toHours()} hours ago")
            return
        }

        var attempts = 0
        val maxAttempts = 3
        val backoffDelay = 5000L
        
        while (attempts < maxAttempts) {
            try {
                artworkProvider.fetchArtwork()
                    .onSuccess { (path, metadata) ->
                        val storedPath = artworkStorage.saveArtwork(path, metadata)
                        wallpaperService.setWallpaper(storedPath)
                        _currentArtwork.value = storedPath to metadata
                        lastSuccessfulArtwork = storedPath to metadata
                        lastUpdateTime = System.currentTimeMillis()
                        historyManager.addToHistory(metadata)
                        saveState(metadata)
                        // Clean up cache files after successful save
                        artworkProvider.cleanupCacheFiles(metadata.id)
                        logger.info("Successfully updated wallpaper: ${metadata.title} by ${metadata.artist}")
                        return
                    }
                attempts++
            } catch (e: Exception) {
                logger.error("Attempt $attempts failed", e)
                delay(backoffDelay * attempts)
            }
        }
        throw IllegalStateException("Failed to update wallpaper after $maxAttempts attempts")
    }

    suspend fun setWallpaper(path: Path, metadata: ArtworkMetadata) {
        wallpaperService.setWallpaper(path)
        lastSuccessfulArtwork = path to metadata
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
} 