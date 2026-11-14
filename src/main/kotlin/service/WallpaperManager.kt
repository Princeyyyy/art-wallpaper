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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.io.path.nameWithoutExtension
import MacPlatform
import exception.NetworkErrors
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
    private var lastRotationDate: String? = null
    private var lastScheduledSlot: String? = null
    @Volatile private var pendingNetworkRetry: Boolean = false
    @Volatile private var scheduleChangedBypassCooldown: Boolean = false
    private var globalSettingsSnapshot: Settings = Settings.currentSettings.value
    private val stateWriteLock = Any()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _currentArtwork = MutableStateFlow<Pair<Path, ArtworkMetadata>?>(null)
    val currentArtwork: StateFlow<Pair<Path, ArtworkMetadata>?> = _currentArtwork.asStateFlow()

    val isPendingScheduledRetry: Boolean
        get() = pendingNetworkRetry

    internal val currentActiveSettings: Settings
        get() {
            val globalCurrent = Settings.currentSettings.value
            if (globalCurrent !== globalSettingsSnapshot) {
                globalSettingsSnapshot = globalCurrent
                settings = globalCurrent
            }
            return settings
        }

    private fun currentScheduleSlot(active: Settings = currentActiveSettings): String =
        "%02d:%02d".format(active.updateTimeHour.coerceIn(0, 23), active.updateTimeMinute.coerceIn(0, 59))

    private fun isTodayScheduleSatisfied(todayStr: String, currentSlot: String): Boolean {
        if (lastRotationDate != todayStr) return false
        return lastScheduledSlot == null || lastScheduledSlot == currentSlot
    }

    /**
     * Returns true when the current wall-clock time is at or past today's designated
     * rotation schedule AND today's rotation has not yet been completed for the active slot.
     */
    fun isScheduledRotationDue(now: LocalDateTime = LocalDateTime.now()): Boolean {
        val todayStr = now.toLocalDate().toString()
        val active = currentActiveSettings
        val currentSlot = currentScheduleSlot(active)
        if (isTodayScheduleSatisfied(todayStr, currentSlot)) return false

        val scheduledToday = LocalDateTime.of(
            now.toLocalDate(),
            LocalTime.of(active.updateTimeHour.coerceIn(0, 23), active.updateTimeMinute.coerceIn(0, 59))
        )
        return !now.isBefore(scheduledToday)
    }

    /**
     * Computes the exact next scheduled rotation [LocalDateTime] for the background ticker.
     */
    fun getNextScheduledDateTime(now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val active = currentActiveSettings
        val scheduledToday = LocalDateTime.of(
            now.toLocalDate(),
            LocalTime.of(active.updateTimeHour.coerceIn(0, 23), active.updateTimeMinute.coerceIn(0, 59))
        )
        val todayStr = now.toLocalDate().toString()
        val currentSlot = currentScheduleSlot(active)

        return when {
            !isTodayScheduleSatisfied(todayStr, currentSlot) && !now.isBefore(scheduledToday) -> scheduledToday
            now.isBefore(scheduledToday) && !isTodayScheduleSatisfied(todayStr, currentSlot) -> scheduledToday
            else -> scheduledToday.plusDays(1)
        }
    }

    fun updateSettings(newSettings: Settings) {
        val oldHour = this.settings.updateTimeHour
        val oldMin = this.settings.updateTimeMinute
        val scheduleChanged = (oldHour != newSettings.updateTimeHour || oldMin != newSettings.updateTimeMinute)
        this.settings = newSettings
        this.globalSettingsSnapshot = Settings.currentSettings.value

        if (scheduleChanged) {
            logger.info(
                "Daily rotation schedule updated from %02d:%02d to %02d:%02d — arming tracker for new time".format(
                    oldHour, oldMin, newSettings.updateTimeHour, newSettings.updateTimeMinute
                )
            )
            lastRotationDate = null
            lastScheduledSlot = null
            scheduleChangedBypassCooldown = true
            pendingNetworkRetry = false
            _currentArtwork.value?.second?.let { activeMeta ->
                saveState(activeMeta, lastUpdateTime, lastScheduledUpdateTime, null, null)
            }
        }
    }

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
        val lastScheduledUpdateTime: Long,
        val lastRotationDate: String? = null,
        val lastScheduledSlot: String? = null
    )

    init {
        // Load last saved state on construction.
        // CRITICAL: Load persisted state FIRST so lastRotationDate, lastUpdateTime, and
        // lastScheduledUpdateTime are populated before syncWithActiveDesktopWallpaper()
        // calls saveState().
        try {
            var restored = false

            // Priority 1: Restore from wallpaper_state.json to recover timing fields
            if (stateFile.exists()) {
                try {
                    val savedState = json.decodeFromString<SavedState>(stateFile.readText())
                    lastUpdateTime = savedState.lastUpdateTime
                    lastScheduledUpdateTime = savedState.lastScheduledUpdateTime
                    lastRotationDate = savedState.lastRotationDate
                    lastScheduledSlot = savedState.lastScheduledSlot

                    val storedPath = artworkStorage.getArtworkPath(savedState.metadata.id)
                    if (storedPath != null && storedPath.exists()) {
                        _currentArtwork.value = storedPath to savedState.metadata
                        lastSuccessfulArtwork = storedPath to savedState.metadata
                        restored = true
                    }
                } catch (parseEx: Exception) {
                    logger.warn("Wallpaper state file unparseable, starting fresh: ${parseEx.message}")
                }
            }

            // Priority 2: Synchronize with what the OS desktop actually shows (preserving timing)
            if (syncWithActiveDesktopWallpaper()) {
                restored = true
            }

            // Priority 3: Fallback to the latest stored artwork on disk or history
            if (!restored) {
                recoverLatestArtworkFromStorage()
            }
        } catch (e: Exception) {
            logger.warn("Wallpaper state init failed, starting fresh: ${e.message}")
            recoverLatestArtworkFromStorage()
        }
    }

    /**
     * Ensures the in-memory state and UI are 100% synchronized with the active
     * desktop wallpaper set in the operating system.
     */
    fun syncWithActiveDesktopWallpaper(): Boolean {
        try {
            val osWallpaperPath = wallpaperService.getCurrentWallpaper()
            if (osWallpaperPath != null && osWallpaperPath.exists()) {
                val id = osWallpaperPath.nameWithoutExtension
                val meta = artworkStorage.getArtworkMetadata(id)
                if (meta != null) {
                    val currentId = _currentArtwork.value?.second?.id
                    if (currentId != id) {
                        logger.info("Synchronized with active OS desktop wallpaper: ${meta.title} ($id)")
                        _currentArtwork.value = osWallpaperPath to meta
                        lastSuccessfulArtwork = osWallpaperPath to meta
                        val effectiveUpdateTime = if (lastUpdateTime > 0) {
                            lastUpdateTime
                        } else {
                            try {
                                Files.getLastModifiedTime(osWallpaperPath).toMillis()
                            } catch (_: Exception) {
                                meta.fetchedAt
                            }
                        }
                        saveState(meta, effectiveUpdateTime, lastScheduledUpdateTime, lastRotationDate, lastScheduledSlot)
                    }
                    return true
                }
            }
        } catch (e: Exception) {
            logger.warn("Could not sync with OS desktop wallpaper: ${e.message}")
        }
        return false
    }

    /**
     * Resiliently recovers the most recent valid artwork from storage or history.
     */
    private fun recoverLatestArtworkFromStorage() {
        try {
            // Priority 1: Check the most recently modified artwork in storage
            val latestStored = artworkStorage.getLatestStoredArtwork()
            if (latestStored != null && latestStored.first.exists()) {
                logger.info("Recovered active artwork from latest stored file: ${latestStored.second.title} (${latestStored.second.id})")
                _currentArtwork.value = latestStored
                lastSuccessfulArtwork = latestStored
                return
            }

            // Priority 2: Fallback to history
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
        } catch (e: Exception) {
            logger.warn("Could not recover artwork from storage: ${e.message}")
        }
    }

    suspend fun checkAndUpdateIfNeeded() {
        val now = System.currentTimeMillis()
        val nowLocal = LocalDateTime.now()
        val todayStr = nowLocal.toLocalDate().toString()
        val currentSlot = currentScheduleSlot()

        // Guard 1: If today's rotation has already occurred and was updated recently, preserve it
        if (_currentArtwork.value != null && isTodayScheduleSatisfied(todayStr, currentSlot)) {
            logger.info("No update needed - today's rotation ($todayStr @ $currentSlot) is already complete")
            return
        }

        if (_currentArtwork.value != null && !scheduleChangedBypassCooldown && lastUpdateTime > 0 &&
            (now - lastUpdateTime) < Duration.ofHours(1).toMillis() && lastRotationDate == todayStr
        ) {
            logger.info("No update needed - current artwork was updated recently (${(now - lastUpdateTime) / 1000}s ago)")
            return
        }

        // If in-memory state is already active and populated, evaluate both daily schedule and interval
        if (_currentArtwork.value != null && lastUpdateTime > 0) {
            if (isScheduledRotationDue(nowLocal) || shouldUpdate(lastUpdateTime)) {
                logger.info("Update needed - scheduled daily rotation due or interval elapsed")
                updateWallpaper()
            } else {
                logger.info(
                    "No update needed - last update was ${Duration.ofMillis(now - lastUpdateTime).toHours()}h ago, " +
                        "next scheduled at ${getNextScheduledDateTime(nowLocal)}"
                )
            }
            return
        }

        val state = loadState()

        if (state == null) {
            val active = _currentArtwork.value
            if (active != null) {
                val stamp = if (lastUpdateTime > 0) lastUpdateTime else now
                val schedStamp = if (lastScheduledUpdateTime > 0) lastScheduledUpdateTime else now
                val rotDate = lastRotationDate ?: todayStr
                val rotSlot = lastScheduledSlot ?: currentSlot
                lastRotationDate = rotDate
                lastScheduledSlot = rotSlot
                saveState(active.second, stamp, schedStamp, rotDate, rotSlot)
                logger.info("Persisted active artwork state; skipping update")
                return
            }

            val activeSettings = currentActiveSettings
            if (!activeSettings.isFirstRun) {
                logger.info("Update needed - no state or active artwork found")
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
                lastRotationDate = state.lastRotationDate
                lastScheduledSlot = state.lastScheduledSlot
            } else {
                recoverLatestArtworkFromStorage()
            }
        }

        // If after restoration memory is STILL empty, immediately curate initial artwork
        if (_currentArtwork.value == null && !currentActiveSettings.isFirstRun) {
            logger.info("No active artwork on disk — triggering initial curation")
            updateWallpaper()
            return
        }

        if (isTodayScheduleSatisfied(todayStr, currentSlot)) {
            logger.info("Skipping update - today's scheduled rotation ($todayStr) is already complete")
            return
        }

        if (isScheduledRotationDue(nowLocal) || shouldUpdate(state.lastUpdateTime)) {
            if (!scheduleChangedBypassCooldown && lastUpdateTime > 0 &&
                (now - lastUpdateTime) < Duration.ofHours(1).toMillis() && lastRotationDate == todayStr
            ) {
                logger.info("Skipping update - wallpaper was updated within the last hour today")
                return
            }
            logger.info("Update needed - scheduled rotation due or interval elapsed")
            updateWallpaper()
        } else {
            logger.info(
                "No update needed - last update was ${Duration.ofMillis(now - state.lastUpdateTime).toHours()} hours ago"
            )
        }
    }

    private fun shouldUpdate(lastUpdateTime: Long): Boolean {
        if (lastUpdateTime <= 0) return true
        val now = System.currentTimeMillis()
        val hoursSinceLastUpdate = Duration.ofMillis(now - lastUpdateTime).toHours()
        return hoursSinceLastUpdate >= currentActiveSettings.updateInterval.toHours()
    }

    private suspend fun updateWallpaper(forceUpdate: Boolean = false) {
        if (!updateMutex.tryLock()) {
            logger.info("Update already in progress, skipping")
            return
        }

        try {
            updateRetryPolicy.execute {
                if (!connectivityChecker.isNetworkAvailable()) {
                    throw NetworkUnavailableException()
                }

                val artwork = artworkProvider.fetchArtwork().getOrThrow()
                val storedPath = artworkStorage.saveArtwork(artwork.first, artwork.second)

                wallpaperService.setWallpaper(storedPath).getOrThrow()
                syncLockScreenIfEnabled(storedPath)

                val now = System.currentTimeMillis()
                val rotationDate = LocalDate.now().toString()
                val slot = currentScheduleSlot()
                lastSuccessfulArtwork = storedPath to artwork.second
                _currentArtwork.value = storedPath to artwork.second
                lastScheduledUpdateTime = now
                lastUpdateTime = now
                lastRotationDate = rotationDate
                lastScheduledSlot = slot
                pendingNetworkRetry = false
                scheduleChangedBypassCooldown = false

                // Add to history so it isn't repeated
                historyManager.addToHistory(artwork.second)

                saveState(artwork.second, now, now, rotationDate, slot)
                artworkProvider.cleanupCacheFiles(artwork.second.id)
                logger.info("Wallpaper updated: ${artwork.second.title} (date=$rotationDate, slot=$slot)")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (NetworkErrors.isNetworkFailure(e)) {
                pendingNetworkRetry = true
                logger.warn("Wallpaper update deferred - network unreachable (${NetworkErrors.describe(e)}); armed for fast retry")
            } else {
                logger.error("Failed to update wallpaper after retries", e)
            }
            throw e
        } finally {
            updateMutex.unlock()
        }
    }

    private fun loadState(): SavedState? {
        return try {
            synchronized(stateWriteLock) {
                if (stateFile.exists()) {
                    json.decodeFromString<SavedState>(stateFile.readText())
                } else null
            }
        } catch (e: Exception) {
            logger.error("Failed to load state", e)
            null
        }
    }

    private fun saveState(
        metadata: ArtworkMetadata,
        updateTime: Long,
        scheduledUpdateTime: Long,
        rotationDate: String? = lastRotationDate,
        scheduledSlot: String? = lastScheduledSlot
    ) {
        try {
            synchronized(stateWriteLock) {
                stateFile.parent?.createDirectories()
                val state = SavedState(
                    metadata = metadata,
                    lastUpdateTime = updateTime,
                    lastScheduledUpdateTime = scheduledUpdateTime,
                    lastRotationDate = rotationDate,
                    lastScheduledSlot = scheduledSlot
                )
                val encoded = json.encodeToString(state)
                val tempFile = stateFile.resolveSibling("${stateFile.fileName}.tmp")
                tempFile.writeText(encoded)
                try {
                    Files.move(tempFile, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: Exception) {
                    Files.move(tempFile, stateFile, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            logger.debug("Saved state for artwork: ${metadata.title} (rotationDate=$rotationDate, slot=$scheduledSlot)")
        } catch (e: Exception) {
            logger.error("Failed to save wallpaper state", e)
        }
    }

    suspend fun start() {
        if (job != null && job?.isActive == true) {
            logger.info("Wallpaper manager already running")
            return
        }

        logger.info("Starting wallpaper manager")
        job = scope.launch {
            try {
                if (!currentActiveSettings.isFirstRun) {
                    checkAndUpdateIfNeeded()
                    // Ensure lock screen is synchronized on startup if user enabled lock screen sync
                    if (!MacPlatform.isMac && currentActiveSettings.syncLockScreen) {
                        _currentArtwork.value?.first?.takeIf { it.exists() }?.let { activePath ->
                            syncLockScreenIfEnabled(activePath)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
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
        val todayStr = now.toLocalDate().toString()
        val activeSettings = currentActiveSettings
        val currentSlot = currentScheduleSlot(activeSettings)
        val scheduledTime = LocalDateTime.of(
            now.toLocalDate(),
            LocalTime.of(
                activeSettings.updateTimeHour.coerceIn(0, 23),
                activeSettings.updateTimeMinute.coerceIn(0, 59)
            )
        )

        // Guard 1: Already rotated today for the current schedule slot.
        if (isTodayScheduleSatisfied(todayStr, currentSlot)) {
            logger.debug("Skipping periodic check: wallpaper already rotated today ($todayStr @ $currentSlot)")
            return
        }

        // Guard 2: Wallpaper was set within the last 15 minutes today (unless schedule was explicitly changed by user).
        val nowMs = System.currentTimeMillis()
        if (!scheduleChangedBypassCooldown && lastRotationDate == todayStr &&
            lastUpdateTime > 0 && (nowMs - lastUpdateTime) < Duration.ofMinutes(15).toMillis()
        ) {
            logger.debug("Skipping periodic check: wallpaper was updated recently (${(nowMs - lastUpdateTime) / 1000}s ago)")
            return
        }

        if (!now.isBefore(scheduledTime)) {
            logger.info("Scheduled rotation triggered: date=$todayStr, slot=$currentSlot (scheduled=$scheduledTime, now=$now)")
            scope.launch {
                try {
                    updateWallpaper()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (NetworkErrors.isNetworkFailure(e)) {
                        logger.warn("Scheduled wallpaper update deferred - network unreachable (${NetworkErrors.describe(e)})")
                    } else {
                        logger.error("Error during scheduled wallpaper update", e)
                    }
                }
            }
        } else {
            logger.debug("No update needed yet - next update scheduled for $scheduledTime")
        }
    }

    fun setWallpaperManually(path: Path, metadata: ArtworkMetadata) {
        wallpaperService.setWallpaper(path).onSuccess {
            syncLockScreenIfEnabled(path)
            val now = System.currentTimeMillis()
            val rotationDate = LocalDate.now().toString()
            val slot = currentScheduleSlot()
            lastUpdateTime = now
            lastScheduledUpdateTime = now
            lastRotationDate = rotationDate
            lastScheduledSlot = slot
            pendingNetworkRetry = false
            scheduleChangedBypassCooldown = false
            _currentArtwork.value = path to metadata
            lastSuccessfulArtwork = path to metadata
            saveState(metadata, now, now, rotationDate, slot)
            scope.launch {
                historyManager.addToHistory(metadata)
            }
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
        } catch (e: CancellationException) {
            throw e
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
        syncLockScreenIfEnabled(storedPath)
        _currentArtwork.value = storedPath to metadata
        val now = System.currentTimeMillis()
        lastUpdateTime = now
        saveState(metadata, now, lastScheduledUpdateTime, lastRotationDate, lastScheduledSlot)
        logger.info("Wallpaper reprocessed with fit mode $fitMode: ${metadata.title}")
        storedPath
    }

    private fun syncLockScreenIfEnabled(imagePath: Path) {
        if (MacPlatform.isMac) return
        val enabled = currentActiveSettings.syncLockScreen || settings.syncLockScreen
        if (!enabled) return
        wallpaperService.setLockScreenWallpaper(imagePath).onFailure { error ->
            logger.warn("Lock screen sync failed: ${error.message}")
        }
    }

    suspend fun syncLockScreenWithCurrentWallpaper(): Result<Unit> {
        if (MacPlatform.isMac) return Result.success(Unit)
        return withContext(Dispatchers.IO) {
            val currentPath = _currentArtwork.value?.first?.takeIf { it.exists() }
                ?: wallpaperService.getCurrentWallpaper()?.takeIf { it.exists() }
                ?: return@withContext Result.failure(IllegalStateException("No active wallpaper available to sync to lock screen"))
            wallpaperService.setLockScreenWallpaper(currentPath)
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
        repeat(maxAttempts - 1) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (NetworkErrors.isNetworkFailure(e)) {
                    throw e
                }
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
                delay(currentDelay)
            }
        }
        return block() // Last attempt
    }
}
