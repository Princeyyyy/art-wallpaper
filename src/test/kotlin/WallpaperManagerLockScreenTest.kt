import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import service.ConnectivityChecker
import service.HistoryManager
import service.WallpaperManager
import java.nio.file.Files
import java.time.Instant
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class WallpaperManagerLockScreenTest {
    private val tempDir = Files.createTempDirectory("wm-lockscreen-test")

    @AfterTest
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }

    private fun makeMetadata(id: String = "lock-test") = ArtworkMetadata(
        id = id,
        title = "Lock Screen Art",
        artist = "Artist",
        year = 2024,
        description = null,
        source = "met",
        imageUrl = "https://example.com/$id.jpg",
        fetchedAt = Instant.now().epochSecond
    )

    @Test
    fun `syncs lock screen on manual wallpaper set only when syncLockScreen is enabled`() = runTest {
        if (MacPlatform.isMac) return@runTest

        val wallpaperService = mockk<WallpaperService>(relaxed = true)
        every { wallpaperService.getCurrentWallpaper() } returns null
        every { wallpaperService.setWallpaper(any()) } returns Result.success(Unit)
        every { wallpaperService.setLockScreenWallpaper(any()) } returns Result.success(Unit)

        val img = tempDir.resolve("art.jpg")
        img.writeBytes(byteArrayOf(1, 2, 3))

        val managerOff = WallpaperManager(
            wallpaperService = wallpaperService,
            artworkProvider = mockk(relaxed = true),
            artworkStorage = mockk(relaxed = true),
            historyManager = mockk(relaxed = true),
            connectivityChecker = mockk(relaxed = true),
            settings = Settings(isFirstRun = false, syncLockScreen = false),
            stateFile = tempDir.resolve("state1.json")
        )
        managerOff.setWallpaperManually(img, makeMetadata("1"))
        verify(exactly = 1) { wallpaperService.setWallpaper(img) }
        verify(exactly = 0) { wallpaperService.setLockScreenWallpaper(any()) }

        managerOff.updateSettings(Settings(isFirstRun = false, syncLockScreen = true))
        managerOff.setWallpaperManually(img, makeMetadata("2"))
        verify(exactly = 2) { wallpaperService.setWallpaper(img) }
        verify(exactly = 1) { wallpaperService.setLockScreenWallpaper(img) }

        val syncRes = managerOff.syncLockScreenWithCurrentWallpaper()
        assertTrue(syncRes.isSuccess)
        verify(exactly = 2) { wallpaperService.setLockScreenWallpaper(img) }
    }
}
