import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import service.ConnectivityChecker
import service.HistoryManager
import java.net.ConnectException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandler
import java.nio.channels.UnresolvedAddressException
import java.nio.file.Files
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArtworkProviderOfflineTest {
    private val tempDir = Files.createTempDirectory("art-provider-offline-test")

    @AfterTest
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `falls back to cached artwork on DNS failure without spinning through retries`() = runTest {
        val cacheDir = tempDir.resolve("cache")
        val artworksDir = tempDir.resolve("artworks")
        val metadataDir = tempDir.resolve("metadata")
        val historyFile = tempDir.resolve("history.json")
        Files.createDirectories(cacheDir)
        Files.createDirectories(artworksDir)
        Files.createDirectories(metadataDir)

        val cachedImage = artworksDir.resolve("42.jpg")
        cachedImage.writeBytes(byteArrayOf(1, 2, 3, 4))
        val metadata = ArtworkMetadata(
            id = "42",
            title = "Cached Painting",
            artist = "Test Artist",
            year = 1889,
            description = null,
            source = "met",
            imageUrl = "https://example.com/42.jpg"
        )
        metadataDir.resolve("42.json").writeText(Json.encodeToString(metadata))

        val connectivity = mockk<ConnectivityChecker> {
            every { isNetworkAvailable() } returns true
        }
        val httpClient = mockk<HttpClient>()
        every {
            httpClient.send(any<HttpRequest>(), any<BodyHandler<String>>())
        } throws ConnectException().apply { initCause(UnresolvedAddressException()) }

        val provider = ArtworkProvider(
            client = httpClient,
            cacheDir = cacheDir,
            artworksDir = artworksDir,
            connectivityChecker = connectivity,
            historyManager = HistoryManager(historyFile),
            maxRetries = 3
        )

        val result = provider.fetchArtwork()
        assertTrue(result.isSuccess)
        assertEquals("42", result.getOrThrow().second.id)
        verify(exactly = 1) { httpClient.send<String>(any(), any()) }
    }
}
