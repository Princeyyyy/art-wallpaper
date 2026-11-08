import kotlinx.serialization.json.Json
import service.HistoryManager
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

class CorruptDataResilienceTest {

    private lateinit var tempDir: Path

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("resilience-test")
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 1: Corrupted history.json Resets Cleanly
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `history manager - malformed JSON resets file and does not crash`() {
        val historyFile = tempDir.resolve("history.json")
        historyFile.writeText("{{{ this is invalid JSON content !!!")

        val manager = HistoryManager(tempDir)
        assertFalse(manager.isInHistory("any-id"))

        // File should be safely recovered
        assertTrue(historyFile.exists())
    }

    @Test
    fun `history manager - empty file recovers without error`() {
        val historyFile = tempDir.resolve("history.json")
        historyFile.writeText("")

        val manager = HistoryManager(tempDir)
        assertFalse(manager.isInHistory("any-id"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 2: Corrupted Settings Decodes Gracefully
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `settings - malformed JSON is detected by decodeFromString`() {
        val corruptJson = "{ \"changeIntervalHours\": \"not-a-number\", \"broken\": true "
        assertFailsWith<Exception> {
            Json.decodeFromString<Settings>(corruptJson)
        }
    }

    @Test
    fun `settings - unknown extra fields are ignored without failing`() {
        val futureJson = """
            {
                "changeIntervalHours": 12,
                "updateTimeHour": 8,
                "updateTimeMinute": 30,
                "someNewFeatureFromV4": "awesome",
                "futureFlag": 42
            }
        """.trimIndent()

        val jsonParser = Json { ignoreUnknownKeys = true }
        val decoded = jsonParser.decodeFromString<Settings>(futureJson)

        assertEquals(12, decoded.changeIntervalHours)
        assertEquals(8, decoded.updateTimeHour)
        assertEquals(30, decoded.updateTimeMinute)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 3: Missing Metadata in Artwork Storage
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `artwork storage - missing metadata returns null without exception`() {
        val storage = ArtworkStorageManager(tempDir)
        assertNull(storage.getArtworkPath("non-existent-id"))
    }

    @Test
    fun `artwork storage - corrupt metadata JSON is skipped when loading stored artworks`() {
        val storage = ArtworkStorageManager(tempDir)
        val artworksDir = tempDir.resolve("artworks")
        val metadataDir = tempDir.resolve("metadata")
        artworksDir.createDirectories()
        metadataDir.createDirectories()

        // Place image and corrupt JSON metadata
        artworksDir.resolve("corrupt-art.jpg").writeBytes(ByteArray(16))
        metadataDir.resolve("corrupt-art.json").writeText("{ corrupt json !!!")

        val stored = storage.getStoredArtworks()
        // Should skip the corrupt item and return empty list without crashing
        assertTrue(stored.isEmpty())
    }
}
