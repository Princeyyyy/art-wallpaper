import io.mockk.*
import kotlinx.coroutines.test.runTest
import service.ConnectivityChecker
import service.HistoryManager
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.writeBytes
import kotlin.test.*

class WallpaperManagerTest {

    private lateinit var tempDir: Path
    private lateinit var wallpaperService: WallpaperService
    private lateinit var artworkProvider: ArtworkProvider
    private lateinit var artworkStorage: ArtworkStorageManager
    private lateinit var historyManager: HistoryManager
    private lateinit var connectivityChecker: ConnectivityChecker

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("wm-test")
        wallpaperService = mockk(relaxed = true)
        artworkProvider = mockk(relaxed = true)
        artworkStorage = mockk(relaxed = true)
        historyManager = mockk(relaxed = true)
        connectivityChecker = mockk(relaxed = true)
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

    private fun makeManager(settings: Settings = Settings(isFirstRun = false)): service.WallpaperManager {
        return service.WallpaperManager(
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
    fun `start is idempotent â€” second call does nothing`() = runTest {
        val manager = makeManager()
        manager.start()
        manager.start() // Should log "already running" and return
        // No crash = pass
    }

    @Test
    fun `stop after start cancels job without throwing`() = runTest {
        val manager = makeManager()
        manager.start()
        manager.stop()
        // No exception = pass
    }

    @Test
    fun `stop without start is safe`() {
        val manager = makeManager()
        manager.stop() // No job to cancel â€” should not throw
    }

    @Test
    fun `setNextWallpaperManually fetches artwork and delegates to wallpaperService`() = runTest {
        val meta = makeMetadata("manual-id")
        val fakeImagePath = tempDir.resolve("manual-id.jpg")
        fakeImagePath.writeBytes(ByteArray(16))

        every { connectivityChecker.isNetworkAvailable() } returns true
        coEvery { artworkProvider.fetchArtwork() } returns Result.success(fakeImagePath to meta)
        every { artworkStorage.saveArtwork(any(), any()) } returns fakeImagePath
        every { wallpaperService.setWallpaper(any()) } returns Result.success(Unit)
        every { artworkStorage.getArtworkPath(any()) } returns null

        val manager = makeManager()
        manager.setNextWallpaperManually()

        coVerify { artworkProvider.fetchArtwork() }
        verify { artworkStorage.saveArtwork(fakeImagePath, meta) }
        verify { wallpaperService.setWallpaper(fakeImagePath) }
    }

    @Test
    fun `currentArtwork starts as null when no saved state`() {
        every { artworkStorage.getArtworkPath(any()) } returns null
        val manager = makeManager()
        assertNull(manager.currentArtwork.value)
    }

    @Test
    fun `manual wallpaper curation prevents unexpected auto update on service start`() = runTest {
        val meta = makeMetadata("onboard-id")
        val fakeImagePath = tempDir.resolve("onboard-id.jpg")
        fakeImagePath.writeBytes(ByteArray(16))

        every { wallpaperService.setWallpaper(fakeImagePath) } returns Result.success(Unit)
        every { artworkStorage.getArtworkPath(any()) } returns fakeImagePath

        val manager = makeManager(Settings(isFirstRun = false))
        manager.setWallpaperManually(fakeImagePath, meta)

        assertEquals("onboard-id", manager.currentArtwork.value?.second?.id)

        // Now run checkAndUpdateIfNeeded (as ServiceController would when service starts)
        manager.checkAndUpdateIfNeeded()

        // Verify artworkProvider was NOT called to fetch/replace the wallpaper
        coVerify(exactly = 0) { artworkProvider.fetchArtwork() }
    }

    @Test
    fun `checkAndUpdate skips update if wallpaper was set today`() = runTest {
        val meta = makeMetadata("today-id")
        val fakeImagePath = tempDir.resolve("today-id.jpg")
        fakeImagePath.writeBytes(ByteArray(16))

        every { wallpaperService.setWallpaper(fakeImagePath) } returns Result.success(Unit)

        val manager = makeManager(Settings(isFirstRun = false))
        manager.setWallpaperManually(fakeImagePath, meta)

        // checkAndUpdate should skip because lastScheduledUpdateTime was set today
        manager.checkAndUpdate()

        coVerify(exactly = 0) { artworkProvider.fetchArtwork() }
    }
}
