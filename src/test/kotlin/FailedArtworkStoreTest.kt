import service.FailedArtworkStore
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FailedArtworkStoreTest {
    private val tempDir = Files.createTempDirectory("failed-artwork-store-test")

    @AfterTest
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `persists failed IDs across reloads and skips duplicates`() {
        val storeFile = tempDir.resolve("failed_artworks.json")
        val store1 = FailedArtworkStore(storeFile)

        store1.markFailedBatch(listOf("101", "102", "101"))
        assertTrue(store1.isFailed("101"))
        assertTrue(store1.isFailed("102"))
        assertFalse(store1.isFailed("103"))
        assertEquals(2, store1.size())

        val store2 = FailedArtworkStore(storeFile)
        assertTrue(store2.isFailed("101"))
        assertTrue(store2.isFailed("102"))
        assertEquals(2, store2.size())
    }

    @Test
    fun `evicts oldest entries when maxEntries is exceeded`() {
        val storeFile = tempDir.resolve("failed_artworks.json")
        val store = FailedArtworkStore(storeFile, maxEntries = 3)

        store.markFailedBatch(listOf("1", "2", "3", "4"))
        assertFalse(store.isFailed("1"))
        assertTrue(store.isFailed("2"))
        assertTrue(store.isFailed("3"))
        assertTrue(store.isFailed("4"))
        assertEquals(3, store.size())
    }
}
