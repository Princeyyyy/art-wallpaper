import data.ArtworkSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import service.HistoryManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*

class MetMuseumSource(
    private val client: HttpClient,
    private val cacheDir: Path,
    private val historyManager: HistoryManager
) : ArtworkSource {
    private val logger = LoggerFactory.getLogger(MetMuseumSource::class.java)
    private val baseUrl = "https://collectionapi.metmuseum.org/public/collection/v1"
    private val departments = listOf(
        11, // European Paintings
        21  // Modern Art
    )
    private val json = Json { ignoreUnknownKeys = true }
    private val maxFetchAttempts = 10
    private val imageProcessor = ImageProcessor()

    // Performance optimization: Cache department object IDs in memory to avoid
    // downloading and decoding 10,000+ IDs on every wallpaper fetch.
    private val departmentCache = ConcurrentHashMap<Int, List<Int>>()

    @Serializable
    private data class ObjectIdsResponse(
        val total: Int,
        val objectIDs: List<Int>
    )

    @Serializable
    private data class ArtworkResponse(
        val objectID: Int,
        val title: String,
        val artistDisplayName: String = "",
        val artistDisplayBio: String = "",
        val artistNationality: String = "",
        val objectDate: String = "",
        val objectBeginDate: Int = 0,
        val objectEndDate: Int = 0,
        val primaryImage: String = "",
        val medium: String = "",
        val dimensions: String = "",
        val department: String = "",
        val culture: String = "",
        val period: String = "",
        val dynasty: String = "",
        val reign: String = "",
        val classification: String = "",
        val repository: String = "",
        val GalleryNumber: String = ""
    )

    private fun parseYear(dateString: String): Int? {
        return try {
            val yearPattern = "\\d{4}".toRegex()
            yearPattern.find(dateString)?.value?.toInt()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun fetchRandomArtwork(): Result<Pair<Path, ArtworkMetadata>> = runCatching {
        withContext(Dispatchers.IO) {
            val selectedDepartment = departments.random()
            logger.info("Selecting artwork from Met Museum API (Dept ID: $selectedDepartment)")

            val objectIds = getDepartmentObjectIds(selectedDepartment)
            if (objectIds.isEmpty()) {
                throw IllegalStateException("No artworks found in department $selectedDepartment")
            }

            var availableIds = objectIds.filter { !historyManager.isInHistory(it.toString()) }
            if (availableIds.isEmpty()) {
                logger.info("All department artworks have been shown, refreshing history cycle")
                historyManager.clearHistory()
                availableIds = objectIds
            }

            val shuffledIds = availableIds.shuffled()
            var lastError: Exception? = null

            for (attempt in 0 until minOf(maxFetchAttempts, shuffledIds.size)) {
                val candidateId = shuffledIds[attempt]
                try {
                    val result = tryFetchArtwork(candidateId)
                    if (result != null) return@withContext result
                } catch (e: Exception) {
                    logger.warn("Candidate $candidateId failed: ${e.message}")
                    lastError = e
                }
            }

            throw lastError ?: IllegalStateException("Could not find a usable artwork after $maxFetchAttempts attempts")
        }
    }

    private suspend fun getDepartmentObjectIds(departmentId: Int): List<Int> = withContext(Dispatchers.IO) {
        val cached = departmentCache[departmentId]
        if (cached != null && cached.isNotEmpty()) {
            return@withContext cached
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/objects?departmentIds=$departmentId"))
            .build()

        val response = client.send(request, BodyHandlers.ofString())
        val decoded = json.decodeFromString<ObjectIdsResponse>(response.body()).objectIDs
        departmentCache[departmentId] = decoded
        decoded
    }

    private suspend fun tryFetchArtwork(objectId: Int): Pair<Path, ArtworkMetadata>? = withContext(Dispatchers.IO) {
        val artworkRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/objects/$objectId"))
            .build()

        val artworkResponse = client.send(artworkRequest, BodyHandlers.ofString())
        val artwork = json.decodeFromString<ArtworkResponse>(artworkResponse.body())

        if (artwork.primaryImage.isBlank()) {
            return@withContext null
        }

        val imageRequest = HttpRequest.newBuilder()
            .uri(URI.create(artwork.primaryImage))
            .build()

        val imageResponse = client.send(imageRequest, BodyHandlers.ofByteArray())
        if (imageResponse.statusCode() != 200 || imageResponse.body().isEmpty()) {
            return@withContext null
        }

        val tempFile = kotlin.io.path.createTempFile(cacheDir, "artwork-$objectId-", ".jpg")
        tempFile.writeBytes(imageResponse.body())

        try {
            val processedFile = imageProcessor.processImage(tempFile)
            tempFile.deleteIfExists()

            val metadata = ArtworkMetadata(
                id = artwork.objectID.toString(),
                title = artwork.title.ifBlank { "Untitled" },
                artist = artwork.artistDisplayName.ifBlank { "Unknown Artist" },
                year = if (artwork.objectBeginDate > 0) artwork.objectBeginDate else parseYear(artwork.objectDate),
                description = buildString {
                    if (artwork.medium.isNotBlank()) append(artwork.medium)
                    if (artwork.dimensions.isNotBlank()) append("\nDimensions: ${artwork.dimensions}")
                    if (artwork.culture.isNotBlank()) append("\nCulture: ${artwork.culture}")
                    if (artwork.period.isNotBlank()) append("\nPeriod: ${artwork.period}")
                    if (artwork.artistDisplayBio.isNotBlank()) append("\nArtist Bio: ${artwork.artistDisplayBio}")
                    if (artwork.repository.isNotBlank()) append("\nCollection: ${artwork.repository}")
                },
                source = "The Metropolitan Museum of Art",
                imageUrl = artwork.primaryImage,
                fetchedAt = System.currentTimeMillis()
            )

            processedFile to metadata
        } catch (e: Exception) {
            tempFile.deleteIfExists()
            throw e
        }
    }
}
