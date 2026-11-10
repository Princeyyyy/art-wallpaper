package service

import ArtworkMetadata
import ArtworkProvider
import ArtworkStorageManager
import Settings
import WallpaperFitMode
import ImageProcessor
import WindowsWallpaperService
import WallpaperService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files
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
    private val wallpaperService: WallpaperService = WallpaperService.create(),
    private val artworkProvider: ArtworkProvider,
    private val artworkStorage: ArtworkStorageManager,
    private val historyManager: HistoryManager,
    private val connectivityChecker: ConnectivityChecker = ConnectivityChecker(),
    private var settings: Settings,
    private val stateFile: Path = Path.of(System.getProperty("user.home"), ".artwallpaper", "wallpaper_state.json")
) {
    private val logger = LoggerFactory.getLogger(WallpaperManager::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null
    private var lastSuccessfulArtwork: Pair<Path, ArtworkMetadata>? = null
    private var lastUpdateTime: Long = 0
    private var lastScheduledUpdateTime: Long = 0
    private val _currentArtwork = MutableStateFlow<Pair<Path, ArtworkMetadata>?>(null)
    val currentArtwork: StateFlow<Pair<Path, ArtworkMetadata>?> = _currentArtwork.asStateFlow()

    private val currentActiveSettings: Settings
        get() = Settings.currentSettings.value.takeIf { !it.isFirstRun || settings.isFirstRun } ?: settings

    fun updateSettings(newSettings: Settings) {
        this.settings = newSettings
    }

    // FIX #2: Replace raw Java Object monitor + boolean flag with a coroutine Mutex.
    // Using synchronized() inside a suspend function blocks a dispatcher thread and
    // can deadlock. Mutex is coroutine-safe and suspends without blocking.
    private val updateMutex = Mutex()

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
        // Load last saved state on construction.
        try {
            var restored = false
            if (stateFile.exists()) {
                val savedState = Json.decodeFromString<SavedState>(stateFile.readText())
                lastUpdateTime = savedState.lastUpdateTime
                lastScheduledUpdateTime = savedState.lastScheduledUpdateTime

                val storedPath = artworkStorage.getArtworkPath(savedState.metadata.id)
                if (storedPath != null && storedPath.exists()) {
                    _currentArtwork.value = storedPath to savedState.metadata
                    lastSuccessfulArtwork = storedPath to savedState.metadata
                    restored = true
                }
            }

            // Fallback: If saved state was missing or pointed to a non-existent image,
            // recover the latest valid artwork from history or disk so the gallery is never blank.
            if (!restored) {
                recoverLatestArtworkFromStorage()
            }
        } catch (e: Exception) {
            logger.warn("Wallpaper state file unparseable, starting fresh: ${e.message}")
            recoverLatestArtworkFromStorage()
        }
    }

    /**
     * Resiliently recovers the most recent valid artwork from history or the artworks directory.
     */
    private fun recoverLatestArtworkFromStorage() {
        try {
            val stored = artworkStorage.getStoredArtworks()
            val historyIds = historyManager.getHistory()
            for (id in historyIds.reversed()) {
                val match = stored.firstOrNull { it.second.id == id && it.first.exists() }
                if (match != null) {
                    logger.info("Recovered active artwork from history: ${match.second.title} ($id)")
                    _currentArtwork.value = match
                    lastSuccessfulArtwork = match
                    return
                }
            }

            // Fallback: use the most recently modified stored artwork
            val latest = stored.filter { it.first.exists() }
                .maxByOrNull { Files.getLastModifiedTime(it.first).toMillis() }
            if (latest != null) {
                logger.info("Recovered active artwork from storage directory: ${latest.second.title} (${latest.second.id})")
                _currentArtwork.value = latest
                lastSuccessfulArtwork = latest
            }
        } catch (e: Exception) {
            logger.warn("Could not recover artwork from storage: ${e.message}")
        }
    }

    suspend fun checkAndUpdateIfNeeded() {
        val now = System.currentTimeMillis()

        // Guard 1: If current artwork is already active and was updated in the last hour, preserve it
        if (_currentArtwork.value != null && lastUpdateTime > 0 && (now - lastUpdateTime) < Duration.ofHours(1).toMillis()) {
            logger.info("No update needed - current artwork was updated recently (${(now - lastUpdateTime) / 1000}s ago)")
            return
        }

        val state = loadState()

        if (state == null) {
            // If we already have an artwork in memory, save it rather than replacing it
            val active = _currentArtwork.value
            if (active != null) {
                val stamp = if (lastUpdateTime > 0) lastUpdateTime else now
                val schedStamp = if (lastScheduledUpdateTime > 0) lastScheduledUpdateTime else now
                saveState(active.second, stamp, schedStamp)
                logger.info("Persisted active artwork state; skipping update")
                return
            }

            // Truly no state and no active artwork
            val activeSettings = currentActiveSettings
            if (!activeSettings.isFirstRun) {
                logger.info("Update needed - last update was too long ago or no state found")
                updateWallpaper()
            } else {
                logger.info("First run active - awaiting onboarding curation")
            }
            return
        }

        // State exists — restore if memory is empty
        if (_currentArtwork.value == null) {
            val storedPath = artworkStorage.getArtworkPath(state.metadata.id)
            if (storedPath != null && storedPath.exists()) {
                _currentArtwork.value = storedPath to state.metadata
                lastSuccessfulArtwork = storedPath to state.metadata
                lastUpdateTime = state.lastUpdateTime
                lastScheduledUpdateTime = state.lastScheduledUpdateTime
            } else {
                recoverLatestArtworkFromStorage()
            }
        }

        // If after restoration memory is STILL empty (e.g. no artwork files on disk)
        // do not stay stuck in an empty state; immediately curate initial artwork
        if (_currentArtwork.value == null && !currentActiveSettings.isFirstRun) {
            logger.info("No active artwork on disk — triggering initial curation")
            updateWallpaper()
            return
        }

        if (shouldUpdate(state.lastUpdateTime)) {
            // Guard: Never overwrite a wallpaper updated within the last hour
            if (lastUpdateTime > 0 && (now - lastUpdateTime) < Duration.ofHours(1).toMillis()) {
                logger.info("Skipping update - wallpaper was updated within the last hour")
                return
            }
            logger.info("Update needed - last update was too long ago")
            updateWallpaper()
        } else {
            logger.info(
                "No update needed - last update was " +
                    "${Duration.ofMillis(now - state.lastUpdateTime).toHours()} hours ago"
            )
        }
    }

    private fun shouldUpdate(lastUpdateTime: Long): Boolean {
        if (lastUpdateTime <= 0) return true
        val now = System.currentTimeMillis()
        val hoursSinceLastUpdate = Duration.ofMillis(now - lastUpdateTime).toHours()
        return hoursSinceLastUpdate >= currentActiveSettings.updateInterval.toHours()
    }

    private fun calculateNextUpdateTime(): LocalDateTime {
        val now = LocalDateTime.now()
        val activeSettings = currentActiveSettings
        val nextUpdate = now.plus(activeSettings.updateInterval)

        return activeSettings.preferredHours.let { hours ->
            val todayPreferred = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hours, 0))
            if (now.isBefore(todayPreferred)) {
                todayPreferred
            } else {
                todayPreferred.plusDays(1)
            }
        } ?: nextUpdate
    }

    // FIX #2: updateMutex.withLock() suspends without blocking a thread,
    // safe to use in a suspend function.
    private suspend fun updateWallpaper(forceUpdate: Boolean = false) {
        if (updateMutex.isLocked) {
            logger.info("Update already in progress, skipping")
            return
        }

        updateMutex.withLock {
            try {
                updateRetryPolicy.execute {
                    if (!connectivityChecker.isNetworkAvailable()) {
                        throw NetworkUnavailableException()
                    }

                    val artwork = artworkProvider.fetchArtwork().getOrThrow()
                    val storedPath = artworkStorage.saveArtwork(artwork.first, artwork.second)

                    // FIX #3: Call setWallpaper once and handle success in one place.
                    wallpaperService.setWallpaper(storedPath).getOrThrow()

                    val now = System.currentTimeMillis()
                    lastSuccessfulArtwork = storedPath to artwork.second
                    _currentArtwork.value = storedPath to artwork.second
                    lastScheduledUpdateTime = now
                    lastUpdateTime = now

                    // Add to history so it isn't repeated
                    historyManager.addToHistory(artwork.second)

                    saveState(artwork.second, now, now)
                    artworkProvider.cleanupCacheFiles(artwork.second.id)
                    logger.info("Wallpaper updated: ${artwork.second.title}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to update wallpaper after retries", e)
                throw e
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

    private fun saveState(metadata: ArtworkMetadata, updateTime: Long, scheduledUpdateTime: Long) {
        try {
            stateFile.parent.createDirectories()
            val state = SavedState(
                metadata = metadata,
                lastUpdateTime = updateTime,
                lastScheduledUpdateTime = scheduledUpdateTime
            )
            stateFile.writeText(Json.encodeToString(state))
            logger.info("Saved state for artwork: ${metadata.title}")
        } catch (e: Exception) {
            logger.error("Failed to save wallpaper state", e)
        }
    }

    suspend fun start() {
        if (job != null) {
            logger.info("Wallpaper manager already running")
            return
        }

        logger.info("Starting wallpaper manager")
        job = scope.launch {
            try {
                if (!currentActiveSettings.isFirstRun) {
                    checkAndUpdateIfNeeded()
                }
            } catch (e: CancellationException) {
                // Normal during shutdown/cancellation; do not log as error
            } catch (e: Exception) {
                logger.error("Error during wallpaper update check", e)
            }
        }
    }

    fun stop() {
        logger.info("Stopping wallpaper manager")
        job?.cancel()
        job = null
    }

    fun checkAndUpdate() {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val activeSettings = currentActiveSettings
        val scheduledTime = LocalDateTime.of(
            today,
            LocalTime.of(activeSettings.updateTimeHour, activeSettings.updateTimeMinute)
        )

        // Safety guard: if a wallpaper was set in the last 15 minutes, never auto-update!
        val nowMs = System.currentTimeMillis()
        if (lastUpdateTime > 0 && (nowMs - lastUpdateTime) < Duration.ofMinutes(15).toMillis()) {
            logger.info("Skipping periodic check: wallpaper was updated recently (${(nowMs - lastUpdateTime) / 1000}s ago)")
            return
        }

        val lastUpdateDate = if (lastScheduledUpdateTime > 0) {
            LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(lastScheduledUpdateTime),
                ZoneId.systemDefault()
            ).toLocalDate()
        } else {
            null
        }

        if ((now.isAfter(scheduledTime) || now.isEqual(scheduledTime)) &&
            (lastUpdateDate == null || !today.isEqual(lastUpdateDate))
        ) {
            logger.info("Update needed - haven't updated today and we're at/past scheduled time $scheduledTime")
            scope.launch {
                try {
                    updateWallpaper()
                } catch (e: CancellationException) {
                    // Normal during job cancellation
                } catch (e: Exception) {
                    logger.error("Error during scheduled wallpaper update", e)
                }
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

    fun setWallpaperManually(path: Path, metadata: ArtworkMetadata) {
        wallpaperService.setWallpaper(path).onSuccess {
            val now = System.currentTimeMillis()
            lastUpdateTime = now
            lastScheduledUpdateTime = now
            _currentArtwork.value = path to metadata
            lastSuccessfulArtwork = path to metadata
            saveState(metadata, now, now)
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

    suspend fun reprocessCurrentWallpaper(fitMode: WallpaperFitMode): Result<Path> = runCatching {
        val current = _currentArtwork.value ?: throw IllegalStateException("No active artwork")
        val (path, metadata) = current
        val processor = ImageProcessor()
        val reprocessed = processor.processImage(path, fitMode)
        val storedPath = artworkStorage.saveArtwork(reprocessed, metadata)
        wallpaperService.setWallpaper(storedPath).getOrThrow()
        _currentArtwork.value = storedPath to metadata
        val now = System.currentTimeMillis()
        lastUpdateTime = now
        saveState(metadata, now, lastScheduledUpdateTime)
        logger.info("Wallpaper reprocessed with fit mode $fitMode: ${metadata.title}")
        storedPath
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
