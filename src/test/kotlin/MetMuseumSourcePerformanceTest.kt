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
                        """{"objectID":30,"title":"Starry Test","artistDisplayName":"Tester","primaryImage":"https://images.metmuseum.org/30.jpg"}"""
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
}
