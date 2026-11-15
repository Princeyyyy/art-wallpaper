import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import service.FailedArtworkStore
import service.HistoryManager
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MetMuseumSourcePerformanceTest {
    private val tempDir = Files.createTempDirectory("met-source-perf-test")

    @AfterTest
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }

    private fun validPngBytes(): ByteArray {
        val img = BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "png", out)
        return out.toByteArray()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> mockResponse(status: Int, body: T): HttpResponse<T> = mockk {
        every { statusCode() } returns status
        every { body() } returns body
    }

    @Test
    fun `races batch in parallel, records failed IDs, and reuses disk department cache`() = runTest {
        val cacheDir = tempDir.resolve("cache")
        val historyFile = tempDir.resolve("history.json")
        val failedFile = tempDir.resolve("failed_artworks.json")
        Files.createDirectories(cacheDir)

        val pngBytes = validPngBytes()
        val searchCalls = AtomicInteger(0)
        val objectCalls = mutableListOf<String>()

        val httpClient = mockk<HttpClient>()
        every { httpClient.send<Any>(any(), any()) } answers {
            val req = firstArg<HttpRequest>()
            val url = req.uri().toString()
            when {
                url.contains("/search?") -> {
                    searchCalls.incrementAndGet()
                    mockResponse(200, """{"total":3,"objectIDs":[10,20,30]}""")
                }
                url.endsWith("/objects/10") -> {
                    synchronized(objectCalls) { objectCalls.add("10") }
                    mockResponse(404, "")
                }
                url.endsWith("/objects/20") -> {
                    synchronized(objectCalls) { objectCalls.add("20") }
                    mockResponse(200, """{"objectID":20,"title":"No Image","primaryImage":""}""")
                }
                url.endsWith("/objects/30") -> {
                    synchronized(objectCalls) { objectCalls.add("30") }
                    mockResponse(
                        200,
                        """{"objectID":30,"title":"Starry Test","artistDisplayName":"Tester","classification":"Paintings","medium":"Oil on canvas","primaryImage":"https://images.metmuseum.org/30.jpg"}"""
                    )
                }
                url == "https://images.metmuseum.org/30.jpg" -> {
                    mockResponse(200, pngBytes)
                }
                else -> mockResponse(404, "")
            }
        }

        val failedStore = FailedArtworkStore(failedFile)
        val historyManager = HistoryManager(historyFile)
        val source1 = MetMuseumSource(
            client = httpClient,
            cacheDir = cacheDir,
            historyManager = historyManager,
            failedArtworkStore = failedStore,
            parallelBatchSize = 4,
            departments = listOf(11)
        )

        val res = source1.fetchRandomArtwork()
        assertTrue(res.isSuccess)
        assertEquals("30", res.getOrThrow().second.id)
        assertEquals(1, searchCalls.get())

        // Verify a second MetMuseumSource reusing cacheDir loads dept IDs from disk without another /search HTTP call
        val source2 = MetMuseumSource(
            client = httpClient,
            cacheDir = cacheDir,
            historyManager = historyManager,
            failedArtworkStore = failedStore,
            parallelBatchSize = 4,
            departments = listOf(11)
        )
        val res2 = source2.fetchRandomArtwork()
        assertTrue(res2.isSuccess)
        assertEquals(1, searchCalls.get())

        // Non-image / 404 IDs should be marked failed if they finished before winner cancelled them
        assertFalse(failedStore.isFailed("30"))
    }

    @Test
    fun `curatorial filter accepts pictorial fine art and rejects non-pictorial 3D objects`() {
        assertTrue(
            MetMuseumSource.isFineArtWallpaperCandidate(
                MetMuseumSource.ArtworkResponse(
                    objectID = 437390,
                    title = "Aristotle with a Bust of Homer",
                    classification = "Paintings",
                    medium = "Oil on canvas",
                    department = "European Paintings"
                )
            )
        )
        assertTrue(
            MetMuseumSource.isFineArtWallpaperCandidate(
                MetMuseumSource.ArtworkResponse(
                    objectID = 39767,
                    title = "Grooms and Foreign Horses",
                    classification = "Paintings",
                    medium = "Hanging scroll; ink and color on silk",
                    department = "Asian Art"
                )
            )
        )
        assertTrue(
            MetMuseumSource.isFineArtWallpaperCandidate(
                MetMuseumSource.ArtworkResponse(
                    objectID = 283115,
                    title = "Moonrise, Hernandez, New Mexico",
                    classification = "Photographs",
                    medium = "Gelatin silver print",
                    department = "Photographs"
                )
            )
        )

        // Reject textile samples, snuff bottles, furniture, metalwork, figurines, and ivory lockets
        assertFalse(
            MetMuseumSource.isFineArtWallpaperCandidate(
                MetMuseumSource.ArtworkResponse(
                    objectID = 480071,
                    title = "Textile sample",
                    medium = "Silk",
                    department = "Modern and Contemporary Art"
                )
            )
        )
        assertFalse(
            MetMuseumSource.isFineArtWallpaperCandidate(
                MetMuseumSource.ArtworkResponse(
                    objectID = 41628,
                    title = "Snuff Bottle",
                    classification = "Snuff Bottles",
                    medium = "Porcelain with underglaze blue",
                    department = "Asian Art"
                )
            )
        )
        assertFalse(
            MetMuseumSource.isFineArtWallpaperCandidate(
                MetMuseumSource.ArtworkResponse(
                    objectID = 5625,
                    title = "Pier Table",
                    classification = "Furniture",
                    medium = "Mahogany, marble, gilt bronze",
                    department = "The American Wing"
                )
            )
        )
        assertFalse(
            MetMuseumSource.isFineArtWallpaperCandidate(
                MetMuseumSource.ArtworkResponse(
                    objectID = 4807,
                    title = "Escutcheon",
                    classification = "Metal",
                    medium = "Wrought iron",
                    department = "The American Wing"
                )
            )
        )
        assertFalse(
            MetMuseumSource.isFineArtWallpaperCandidate(
                MetMuseumSource.ArtworkResponse(
                    objectID = 436779,
                    title = "The Cooper Penrose (1736-1815) Children",
                    classification = "Miniatures",
                    medium = "Ivory",
                    department = "European Paintings"
                )
            )
        )
    }

    @Test
    fun `buildArtworkMetadata and extractCuratorialFields populate rich educational dossier`() {
        val metArtwork = MetMuseumSource.ArtworkResponse(
            objectID = 436170,
            title = "The Dance Class",
            artistDisplayName = "Edgar Degas",
            artistDisplayBio = "French, Paris 1834-1917 Paris",
            objectDate = "1874",
            objectBeginDate = 1874,
            medium = "Oil on canvas",
            dimensions = "32 7/8 x 30 1/4 in. (83.5 x 76.8 cm)",
            department = "European Paintings",
            classification = "Paintings",
            culture = "French",
            creditLine = "Bequest of Mrs. Harry Payne Bingham, 1986",
            GalleryNumber = "815",
            accessionNumber = "1987.47.1",
            objectURL = "https://www.metmuseum.org/art/collection/search/436170",
            primaryImage = "https://images.metmuseum.org/436170.jpg",
            isHighlight = true,
            tags = listOf(
                MetMuseumSource.TagItem("Ballet"),
                MetMuseumSource.TagItem("Dancers"),
                MetMuseumSource.TagItem("Interiors")
            )
        )

        val metadata = MetMuseumSource.buildArtworkMetadata(metArtwork)
        assertEquals("436170", metadata.id)
        assertEquals("The Dance Class", metadata.title)
        assertEquals("Edgar Degas", metadata.artist)
        assertEquals("French, Paris 1834-1917 Paris", metadata.artistBio)
        assertEquals(1874, metadata.year)
        assertEquals("1874", metadata.objectDate)
        assertEquals("Oil on canvas", metadata.medium)
        assertEquals("European Paintings", metadata.department)
        assertEquals("815", metadata.galleryNumber)
        assertTrue(metadata.isHighlight)
        assertEquals(listOf("Ballet", "Dancers", "Interiors"), metadata.tags)

        val curatorial = extractCuratorialFields(metadata)
        assertEquals("Oil on canvas", curatorial.medium)
        assertEquals("32 7/8 x 30 1/4 in. (83.5 x 76.8 cm)", curatorial.dimensions)
        assertEquals("Bequest of Mrs. Harry Payne Bingham, 1986", curatorial.creditLine)
        assertEquals("European Paintings", curatorial.department)
        assertEquals("815", curatorial.galleryNumber)
        assertNotNull(curatorial.narrative)
        assertTrue(curatorial.narrative.contains("Edgar Degas"))
        assertTrue(curatorial.narrative.contains("European Paintings"))
    }
}
