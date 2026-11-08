import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import service.HistoryManager
import java.nio.file.Files
import java.time.Instant
import kotlin.io.path.writeText
import kotlin.test.*

class HistoryManagerTest {

    private lateinit var tempDir: java.nio.file.Path

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("history-test")
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun makeMetadata(id: String) = ArtworkMetadata(
        id = id,
        title = "Title $id",
        artist = "Artist",
        year = 2000,
        description = null,
        source = "met",
        imageUrl = "https://example.com/$id.jpg",
        fetchedAt = Instant.now().epochSecond
    )

    @Test
    fun `fresh history is empty`() {
        val manager = HistoryManager(tempDir)
        assertFalse(manager.isInHistory("any-id"))
    }

    @Test
    fun `addToHistory makes id visible via isInHistory`() = runTest {
        val manager = HistoryManager(tempDir)
        val meta = makeMetadata("art-001")
        manager.addToHistory(meta)
        assertTrue(manager.isInHistory("art-001"))
    }

    @Test
    fun `clearHistory removes all entries`() = runTest {
        val manager = HistoryManager(tempDir)
        manager.addToHistory(makeMetadata("art-001"))
        manager.addToHistory(makeMetadata("art-002"))
        manager.clearHistory()
        assertFalse(manager.isInHistory("art-001"))
        assertFalse(manager.isInHistory("art-002"))
    }

    @Test
    fun `history persists across manager instances`() = runTest {
        val manager1 = HistoryManager(tempDir)
        manager1.addToHistory(makeMetadata("art-persist-1"))
        manager1.addToHistory(makeMetadata("art-persist-2"))

        // Create a second instance reading same directory
        val manager2 = HistoryManager(tempDir)
        assertTrue(manager2.isInHistory("art-persist-1"))
        assertTrue(manager2.isInHistory("art-persist-2"))
    }

    @Test
    fun `legacy metadata format is migrated on load`() {
        // Write a file containing a list of ArtworkMetadata objects (old format)
        val historyFile = tempDir.resolve("history.json")
        val legacyList = listOf(
            makeMetadata("legacy-01"),
            makeMetadata("legacy-02")
        )
        historyFile.writeText(Json.encodeToString(legacyList))

        val manager = HistoryManager(tempDir)
        assertTrue(manager.isInHistory("legacy-01"))
        assertTrue(manager.isInHistory("legacy-02"))
    }

    @Test
    fun `history is trimmed to max 100 items`() = runTest {
        val manager = HistoryManager(tempDir)
        // Add 105 items
        for (i in 1..105) {
            manager.addToHistory(makeMetadata("art-$i"))
        }
        // The last 100 should be present (101..105 are definitely in)
        assertTrue(manager.isInHistory("art-105"))
        assertTrue(manager.isInHistory("art-100"))
        // Items 1..5 were trimmed
        assertFalse(manager.isInHistory("art-1"))
        assertFalse(manager.isInHistory("art-5"))
    }

    @Test
    fun `cleanupHistory removes items beyond limit`() = runTest {
        val manager = HistoryManager(tempDir)
        for (i in 1..20) {
            manager.addToHistory(makeMetadata("item-$i"))
        }
        manager.cleanupHistory(10)
        // Items 11..20 kept, 1..10 removed
        assertTrue(manager.isInHistory("item-20"))
        assertFalse(manager.isInHistory("item-1"))
    }
}
