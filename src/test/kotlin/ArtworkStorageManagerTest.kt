import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.time.Instant
import kotlin.io.path.*
import kotlin.test.*

class ArtworkStorageManagerTest {

    private lateinit var tempDir: java.nio.file.Path

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("storage-test")
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun makeMetadata(id: String) = ArtworkMetadata(
        id = id,
        title = "Title $id",
        artist = "Artist $id",
        year = 2023,
        description = "Desc",
        source = "met",
        imageUrl = "https://example.com/$id.jpg",
        fetchedAt = Instant.now().epochSecond
    )

    private fun createFakeImage(dir: java.nio.file.Path, name: String): java.nio.file.Path {
        val f = dir.resolve(name)
        f.writeBytes(ByteArray(16) { it.toByte() })
        return f
    }

    @Test
    fun `saveArtwork copies image and writes metadata`() {
        val manager = ArtworkStorageManager(tempDir)
        val meta = makeMetadata("art-save-1")
        val srcImage = createFakeImage(tempDir, "src.jpg")

        val saved = manager.saveArtwork(srcImage, meta)
        assertTrue(saved.exists())
        assertTrue(saved.name.contains("art-save-1"))
        val metaFile = tempDir.resolve("metadata/${meta.id}.json")
        assertTrue(metaFile.exists())
        val loaded = Json.decodeFromString<ArtworkMetadata>(metaFile.readText())
        assertEquals(meta.id, loaded.id)
        assertEquals(meta.title, loaded.title)
    }

    @Test
    fun `getArtworkPath returns null for unknown id`() {
        val manager = ArtworkStorageManager(tempDir)
        assertNull(manager.getArtworkPath("does-not-exist"))
    }

    @Test
    fun `getArtworkPath returns valid path after save`() {
        val manager = ArtworkStorageManager(tempDir)
        val meta = makeMetadata("art-path-1")
        val srcImage = createFakeImage(tempDir, "src2.jpg")
        manager.saveArtwork(srcImage, meta)
        val path = manager.getArtworkPath("art-path-1")
        assertNotNull(path)
        assertTrue(path.exists())
    }

    @Test
    fun `getStoredArtworks returns saved pairs`() {
        val manager = ArtworkStorageManager(tempDir)
        val meta1 = makeMetadata("art-stored-1")
        val meta2 = makeMetadata("art-stored-2")
        manager.saveArtwork(createFakeImage(tempDir, "s1.jpg"), meta1)
        manager.saveArtwork(createFakeImage(tempDir, "s2.jpg"), meta2)

        val artworks = manager.getStoredArtworks()
        assertEquals(2, artworks.size)
        val ids = artworks.map { it.second.id }.toSet()
        assertTrue("art-stored-1" in ids)
        assertTrue("art-stored-2" in ids)
    }

    @Test
    fun `cleanup removes excess artworks beyond maxStoredArtworks`() {
        // maxStoredArtworks = 2 for this test
        val manager = ArtworkStorageManager(tempDir, maxStoredArtworks = 2)
        // Save 4 artworks
        for (i in 1..4) {
            val meta = makeMetadata("art-cleanup-$i")
            manager.saveArtwork(createFakeImage(tempDir, "src-$i.jpg"), meta)
            Thread.sleep(10) // Ensure different mtime
        }
        manager.cleanup()
        val remaining = manager.getStoredArtworks()
        // Should have kept only 2
        assertTrue(remaining.size <= 2, "Expected <= 2, got ${remaining.size}")
    }

    @Test
    fun `cleanupOldArtworks removes orphaned jpg files`() {
        val manager = ArtworkStorageManager(tempDir)
        // Add a legitimate artwork
        manager.saveArtwork(createFakeImage(tempDir, "legit.jpg"), makeMetadata("art-legit"))

        // Drop an orphaned jpg directly with no matching metadata
        val artworksDir = tempDir.resolve("artworks")
        val orphaned = artworksDir.resolve("orphan-no-meta.jpg")
        orphaned.writeBytes(ByteArray(8))

        manager.cleanupOldArtworks()
        assertFalse(orphaned.exists(), "Orphaned jpg should have been removed")
        // Legit artwork should still be there
        assertTrue(artworksDir.resolve("art-legit.jpg").exists())
    }
}
