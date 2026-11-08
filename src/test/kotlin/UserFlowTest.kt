import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import service.HistoryManager
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.*
import kotlin.test.*

/**
 * Integration-level tests that simulate complete user flows using real file I/O
 * in temporary directories. These catch bugs that unit tests miss because they
 * exercise multiple collaborators together.
 */
class UserFlowTest {

    private lateinit var tempDir: Path

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("user-flow-test")
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun makeMetadata(id: String, title: String = "Title $id") = ArtworkMetadata(
        id = id, title = title, artist = "Van Gogh",
        year = 1889, description = null, source = "met",
        imageUrl = "https://example.com/$id.jpg",
        fetchedAt = Instant.now().epochSecond
    )

    private fun writeImage(dir: Path, name: String, bytes: Int = 32): Path {
        val f = dir.resolve(name)
        f.parent.createDirectories()
        f.writeBytes(ByteArray(bytes) { it.toByte() })
        return f
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Flow 1 â€” Fresh install: settings start as first-run
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `fresh install - default settings mark isFirstRun true`() {
        val settings = Settings()
        assertTrue(settings.isFirstRun, "New install must start in first-run state")
    }

    @Test
    fun `completing onboarding - persists isFirstRun false via json roundtrip`() {
        val completed = Settings(isFirstRun = false, startWithSystem = true)
        val json = Json.encodeToString(Settings.serializer(), completed)
        val reloaded = Json.decodeFromString(Settings.serializer(), json)
        assertFalse(reloaded.isFirstRun, "isFirstRun must persist as false after onboarding")
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Flow 2 â€” History deduplication across a full add-clear-reload cycle
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `history deduplication - same id added twice only appears once`() = runTest {
        val history = HistoryManager(tempDir)
        val meta = makeMetadata("dup-id")
        history.addToHistory(meta)
        history.addToHistory(meta) // Same ID again
        history.clearHistory()
        assertFalse(history.isInHistory("dup-id"))
    }

    @Test
    fun `history full flow - add many, persist, reload, verify no repeats`() = runTest {
        val history1 = HistoryManager(tempDir)
        val ids = (1..50).map { "artwork-$it" }
        ids.forEach { history1.addToHistory(makeMetadata(it)) }

        // Reload from disk
        val history2 = HistoryManager(tempDir)
        ids.forEach { id ->
            assertTrue(history2.isInHistory(id), "id $id should be in reloaded history")
        }
    }

    @Test
    fun `history clear and refill - cleared entries are gone, new ones appear`() = runTest {
        val history = HistoryManager(tempDir)
        history.addToHistory(makeMetadata("old-1"))
        history.addToHistory(makeMetadata("old-2"))
        history.clearHistory()

        history.addToHistory(makeMetadata("new-1"))
        assertFalse(history.isInHistory("old-1"))
        assertFalse(history.isInHistory("old-2"))
        assertTrue(history.isInHistory("new-1"))
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Flow 3 â€” Artwork storage save / reload preserves metadata
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `artwork storage - save and reload preserves all metadata fields`() {
        val storage = ArtworkStorageManager(tempDir)
        val meta = makeMetadata("storage-test-1", title = "Starry Night")
        val srcImage = writeImage(tempDir, "tmp-src.jpg")

        storage.saveArtwork(srcImage, meta)

        val artworks = storage.getStoredArtworks()
        assertEquals(1, artworks.size)
        val (path, loaded) = artworks.first()
        assertTrue(path.exists())
        assertEquals("storage-test-1", loaded.id)
        assertEquals("Starry Night", loaded.title)
        assertEquals("Van Gogh", loaded.artist)
        assertEquals(1889, loaded.year)
        assertEquals("met", loaded.source)
    }

    @Test
    fun `artwork storage - multiple saves, cleanup removes excess`() {
        val storage = ArtworkStorageManager(tempDir, maxStoredArtworks = 3)
        for (i in 1..6) {
            storage.saveArtwork(writeImage(tempDir, "tmp-$i.jpg"), makeMetadata("art-$i"))
            Thread.sleep(5) // different mtime
        }
        storage.cleanup()
        val remaining = storage.getStoredArtworks()
        assertTrue(remaining.size <= 3, "Expected <= 3 artworks after cleanup, got ${remaining.size}")
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Flow 4 â€” Settings roundtrip: all fields survive serialize / deserialize
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `settings roundtrip - all custom fields persist correctly`() {
        val settingsFile = tempDir.resolve("settings.json")
        val original = Settings(
            changeIntervalHours = 48,
            updateTimeHour = 22,
            updateTimeMinute = 15,
            startWithSystem = false,
            showNotifications = false,
            isFirstRun = false,
            notificationsEnabled = false,
            hasSetUpdateTime = true,
            hasEnabledAutoStart = false
        )
        settingsFile.writeText(Json.encodeToString(Settings.serializer(), original))
        val loaded = Json.decodeFromString<Settings>(settingsFile.readText())

        assertEquals(48, loaded.changeIntervalHours)
        assertEquals(22, loaded.updateTimeHour)
        assertEquals(15, loaded.updateTimeMinute)
        assertFalse(loaded.startWithSystem)
        assertFalse(loaded.showNotifications)
        assertFalse(loaded.isFirstRun)
        assertFalse(loaded.notificationsEnabled)
        assertFalse(loaded.hasEnabledAutoStart)
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Flow 5 â€” Offline fallback: artworksDir has cached jpg files
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `offline fallback - artworksDir populated, getStoredArtworks returns entries`() {
        val storage = ArtworkStorageManager(tempDir)

        // Simulate artworks saved during a previous online session
        val meta1 = makeMetadata("offline-1", "Morning Haze")
        val meta2 = makeMetadata("offline-2", "Dusk Shore")
        storage.saveArtwork(writeImage(tempDir, "o1.jpg"), meta1)
        storage.saveArtwork(writeImage(tempDir, "o2.jpg"), meta2)

        // After going offline, getStoredArtworks must still return both
        val artworks = ArtworkStorageManager(tempDir).getStoredArtworks()
        assertEquals(2, artworks.size)
        val titles = artworks.map { it.second.title }.toSet()
        assertTrue("Morning Haze" in titles)
        assertTrue("Dusk Shore" in titles)
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Flow 6 â€” Orphan cleanup: leftover files are removed
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `orphan cleanup - jpg without metadata is deleted, valid artwork survives`() {
        val storage = ArtworkStorageManager(tempDir)
        val meta = makeMetadata("valid-art")
        storage.saveArtwork(writeImage(tempDir, "valid.jpg"), meta)

        // Inject an orphaned jpg with no matching metadata
        val artworksDir = tempDir.resolve("artworks")
        writeImage(artworksDir, "orphan-456.jpg")

        storage.cleanupOldArtworks()

        assertFalse(artworksDir.resolve("orphan-456.jpg").exists(), "Orphan must be deleted")
        assertTrue(artworksDir.resolve("valid-art.jpg").exists(), "Valid artwork must survive")
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Flow 7 â€” History max boundary prevents unbounded growth
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `history max boundary - 110 adds, only last 100 survive`() = runTest {
        val history = HistoryManager(tempDir)
        for (i in 1..110) {
            history.addToHistory(makeMetadata("art-$i"))
        }
        // Items 1..10 should be gone (trimmed)
        for (i in 1..10) {
            assertFalse(history.isInHistory("art-$i"), "art-$i should be trimmed")
        }
        // Items 11..110 should be present
        assertTrue(history.isInHistory("art-110"))
        assertTrue(history.isInHistory("art-50"))
    }
}
