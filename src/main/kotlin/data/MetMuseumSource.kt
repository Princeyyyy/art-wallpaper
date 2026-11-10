import data.ArtworkSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import service.HistoryManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Path
import java.time.Duration
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
        11, // European Paintings (Rembrandt, Monet, Van Gogh, Vermeer, Cézanne)
        6,  // Asian Art (Hokusai, Hiroshige woodblocks, landscape screens)
        1,  // American Paintings & Decorative Arts (Bierstadt, Sargent, Homer)
        21  // Modern Art (when public domain / has image)
    )
    private val json = Json { ignoreUnknownKeys = true }
    private val maxFetchAttempts = 25
    private val imageProcessor = ImageProcessor()

    // Performance optimization: Cache department object IDs in memory to avoid
    // downloading and decoding 10,000+ IDs on every wallpaper fetch.
    private val departmentCache = ConcurrentHashMap<Int, List<Int>>()

    @Serializable
    private data class ObjectIdsResponse(
        val total: Int = 0,
        val objectIDs: List<Int>? = emptyList()
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
            val candidateDepartments = departments.shuffled()
            var lastError: Exception? = null

            for (deptId in candidateDepartments) {
                try {
                    logger.info("Selecting artwork from Met Museum API (Dept ID: $deptId)")

                    val objectIds = getDepartmentObjectIds(deptId)
                    if (objectIds.isEmpty()) {
                        logger.warn("No artworks found in Dept $deptId, checking next department...")
                        continue
                    }

                    var availableIds = objectIds.filter { !historyManager.isInHistory(it.toString()) }
                    if (availableIds.isEmpty()) {
                        logger.info("All artworks in Dept $deptId have been shown, refreshing history cycle")
                        historyManager.clearHistory()
                        availableIds = objectIds
                    }

                    val shuffledIds = availableIds.shuffled()
                    for (attempt in 0 until minOf(maxFetchAttempts, shuffledIds.size)) {
                        val candidateId = shuffledIds[attempt]
                        try {
                            val result = tryFetchArtwork(candidateId)
                            if (result != null) return@withContext result
                            delay(60)
                        } catch (e: Exception) {
                            logger.debug("Candidate $candidateId in Dept $deptId failed: ${e.message}")
                            lastError = e
                            delay(100)
                        }
                    }

                    logger.warn("Could not find usable image in Dept $deptId after $maxFetchAttempts candidates, trying next department...")
                } catch (e: Exception) {
                    logger.warn("Failed querying Dept $deptId: ${e.message}, trying next department...")
                    lastError = e
                }
            }

            throw lastError ?: IllegalStateException("Could not find a usable artwork across any Met Museum department")
        }
    }

    private suspend fun getDepartmentObjectIds(departmentId: Int): List<Int> = withContext(Dispatchers.IO) {
        val cached = departmentCache[departmentId]
        if (cached != null && cached.isNotEmpty()) {
            return@withContext cached
        }

        // Prefer /search?departmentId=X&hasImages=true&q=* so 100% of candidate IDs have images
        val searchUrl = "$baseUrl/search?departmentId=$departmentId&hasImages=true&q=*"
        val decoded = try {
            val searchRequest = HttpRequest.newBuilder()
                .uri(URI.create(searchUrl))
                .header("User-Agent", "ArtWallpaper-DesktopApp")
                .timeout(Duration.ofSeconds(15))
                .build()
            val response = client.send(searchRequest, BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val resp = json.decodeFromString<ObjectIdsResponse>(response.body())
                resp.objectIDs ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logger.debug("Met Museum search endpoint failed for Dept $departmentId: ${e.message}, falling back to /objects")
            emptyList()
        }

        val finalIds = if (decoded.isNotEmpty()) {
            decoded
        } else {
            // Fallback to /objects?departmentIds=X
            val fallbackRequest = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/objects?departmentIds=$departmentId"))
                .header("User-Agent", "ArtWallpaper-DesktopApp")
                .timeout(Duration.ofSeconds(15))
                .build()
            val fallbackResponse = client.send(fallbackRequest, BodyHandlers.ofString())
            val resp = json.decodeFromString<ObjectIdsResponse>(fallbackResponse.body())
            resp.objectIDs ?: emptyList()
        }

        departmentCache[departmentId] = finalIds
        finalIds
    }

    private suspend fun tryFetchArtwork(objectId: Int): Pair<Path, ArtworkMetadata>? = withContext(Dispatchers.IO) {
        val artworkRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/objects/$objectId"))
            .header("User-Agent", "ArtWallpaper-DesktopApp")
            .timeout(Duration.ofSeconds(15))
            .build()

        val artworkResponse = client.send(artworkRequest, BodyHandlers.ofString())
        if (artworkResponse.statusCode() != 200) {
            return@withContext null
        }
        val artwork = json.decodeFromString<ArtworkResponse>(artworkResponse.body())

        if (artwork.primaryImage.isBlank()) {
            return@withContext null
        }

        val imageRequest = HttpRequest.newBuilder()
            .uri(URI.create(artwork.primaryImage))
            .header("User-Agent", "ArtWallpaper-DesktopApp")
            .timeout(Duration.ofSeconds(20))
            .build()

        val imageResponse = client.send(imageRequest, BodyHandlers.ofByteArray())
        if (imageResponse.statusCode() != 200 || imageResponse.body().isEmpty()) {
            return@withContext null
        }

        val tempFile = kotlin.io.path.createTempFile(cacheDir, "artwork-$objectId-", ".jpg")
        tempFile.writeBytes(imageResponse.body())

        try {
            val fitMode = Settings.currentSettings.value.wallpaperFitMode
            val processedFile = imageProcessor.processImage(tempFile, fitMode)
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
