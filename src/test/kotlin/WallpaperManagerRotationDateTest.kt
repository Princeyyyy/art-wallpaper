import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import service.ConnectivityChecker
import service.HistoryManager
import service.WallpaperManager
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.*

/**
 * Tests that [WallpaperManager.checkAndUpdate] enforces the per-calendar-day
 * rotation anchor (`lastRotationDate`), preventing spurious mid-day updates.
 *
 * All tests use isolated temp directories — no writes to ~/.artwallpaper.
 */
class WallpaperManagerRotationDateTest {

    private lateinit var tempDir: Path
    private lateinit var wallpaperService: WallpaperService
    private lateinit var artworkProvider: ArtworkProvider
    private lateinit var artworkStorage: ArtworkStorageManager
    private lateinit var historyManager: HistoryManager
    private lateinit var connectivityChecker: ConnectivityChecker

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("wm-rotation-test")
        wallpaperService = mockk(relaxed = true)
        artworkProvider = mockk(relaxed = true)
        artworkStorage = mockk(relaxed = true)
        historyManager = mockk(relaxed = true)
        connectivityChecker = mockk(relaxed = true)

        every { wallpaperService.getCurrentWallpaper() } returns null
        every { artworkStorage.getLatestStoredArtwork() } returns null
        every { artworkStorage.getStoredArtworks() } returns emptyList()
        every { historyManager.getHistory() } returns emptyList()
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun makeMetadata(id: String = "test-id") = ArtworkMetadata(
        id = id, title = "Test", artist = "Artist",
        year = 2023, description = null, source = "met",
        imageUrl = "https://example.com/$id.jpg",
        fetchedAt = Instant.now().epochSecond
    )

    private fun makeManager(settings: Settings = Settings(isFirstRun = false)): WallpaperManager {
        return WallpaperManager(
            wallpaperService = wallpaperService,
            artworkProvider = artworkProvider,
            artworkStorage = artworkStorage,
            historyManager = historyManager,
            connectivityChecker = connectivityChecker,
            settings = settings,
            stateFile = tempDir.resolve("wallpaper_state.json")
        )
    }

    @Test
    fun `checkAndUpdate skips when lastRotationDate matches today`() = runTest {
        val meta = makeMetadata("already-rotated")
        val fakeImagePath = tempDir.resolve("already-rotated.jpg")
        fakeImagePath.writeBytes(ByteArray(16))

        every { wallpaperService.setWallpaper(fakeImagePath) } returns Result.success(Unit)

        val manager = makeManager()
        // Set the wallpaper manually — this writes lastRotationDate = today
        manager.setWallpaperManually(fakeImagePath, meta)

        // Now run checkAndUpdate — it must NOT call fetchArtwork because today is already done
        manager.checkAndUpdate()

        coVerify(exactly = 0) { artworkProvider.fetchArtwork() }
    }

    @Test
    fun `checkAndUpdate skips when state file has todays lastRotationDate`() = runTest {
        val meta = makeMetadata("state-file-id")
        val fakeImagePath = tempDir.resolve("state-file-id.jpg")
        fakeImagePath.writeBytes(ByteArray(16))

        every { artworkStorage.getArtworkPath("state-file-id") } returns fakeImagePath

        // Write a state file with lastRotationDate = today before constructing the manager
        val todayStr = java.time.LocalDate.now().toString()
        val stateFile = tempDir.resolve("wallpaper_state.json")
        stateFile.writeText(
            """{"metadata":{"id":"state-file-id","title":"Test","artist":"Artist","year":2023,"description":null,"source":"met","imageUrl":"https://example.com/state-file-id.jpg","fetchedAt":${Instant.now().epochSecond}},"lastUpdateTime":${System.currentTimeMillis()},"lastScheduledUpdateTime":${System.currentTimeMillis()},"lastRotationDate":"$todayStr"}"""
        )

        // Manager reads state on construction — lastRotationDate should be today
        val manager = makeManager()
        manager.checkAndUpdate()

        coVerify(exactly = 0) { artworkProvider.fetchArtwork() }
    }

    @Test
    fun `checkAndUpdate proceeds when lastRotationDate is yesterday`() = runTest {
        val meta = makeMetadata("yesterday-id")
        val fakeImagePath = tempDir.resolve("yesterday-id.jpg")
        fakeImagePath.writeBytes(ByteArray(16))

        every { artworkStorage.getArtworkPath("yesterday-id") } returns fakeImagePath
        every { connectivityChecker.isNetworkAvailable() } returns true
        coEvery { artworkProvider.fetchArtwork() } returns Result.success(fakeImagePath to meta)
        every { artworkStorage.saveArtwork(any(), any()) } returns fakeImagePath
        every { wallpaperService.setWallpaper(any()) } returns Result.success(Unit)

        // Write state with yesterday's rotation date
        val yesterdayStr = java.time.LocalDate.now().minusDays(1).toString()
        val stateFile = tempDir.resolve("wallpaper_state.json")
        stateFile.writeText(
            """{"metadata":{"id":"yesterday-id","title":"Test","artist":"Artist","year":2023,"description":null,"source":"met","imageUrl":"https://example.com/yesterday-id.jpg","fetchedAt":${Instant.now().epochSecond}},"lastUpdateTime":${System.currentTimeMillis() - 86400_000},"lastScheduledUpdateTime":${System.currentTimeMillis() - 86400_000},"lastRotationDate":"$yesterdayStr"}"""
        )

        val manager = makeManager(Settings(
            isFirstRun = false,
            updateTimeHour = 0,     // scheduled for midnight — always in the past
            updateTimeMinute = 0
        ))
        manager.checkAndUpdate()

        // Should have fetched because yesterday != today
        coVerify(timeout = 3000, atLeast = 1) { artworkProvider.fetchArtwork() }
    }

    @Test
    fun `setWallpaperManually records today as lastRotationDate preventing follow-up auto update`() = runTest {
        val meta = makeMetadata("manual-prevent-id")
        val fakeImagePath = tempDir.resolve("manual-prevent-id.jpg")
        fakeImagePath.writeBytes(ByteArray(16))

        every { wallpaperService.setWallpaper(fakeImagePath) } returns Result.success(Unit)

        val manager = makeManager()
        manager.setWallpaperManually(fakeImagePath, meta)

        // Simulate the periodic check firing shortly after (as ServiceController would)
        manager.checkAndUpdate()

        coVerify(exactly = 0) { artworkProvider.fetchArtwork() }
    }
}
