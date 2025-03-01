import data.ArtworkSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import service.HistoryManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.random.Random

class MetMuseumSource(
    private val client: HttpClient,
    private val cacheDir: Path,
    private val historyManager: HistoryManager
) : ArtworkSource {
    private val logger = LoggerFactory.getLogger(MetMuseumSource::class.java)
    private val baseUrl = "https://collectionapi.metmuseum.org/public/collection/v1"
    private val departments = listOf(
        11,  // European Paintings
        21   // Modern Art
    )
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ObjectIdsResponse(
        val total: Int,
        val objectIDs: List<Int>
    )

    @Serializable
    private data class ArtworkResponse(
        val objectID: Int,
        val title: String,
        val artistDisplayName: String,
        val artistDisplayBio: String,
        val artistNationality: String,
        val objectDate: String,
        val objectBeginDate: Int,
        val objectEndDate: Int,
        val primaryImage: String,
        val medium: String,
        val dimensions: String,
        val department: String,
        val culture: String,
        val period: String,
        val dynasty: String,
        val reign: String,
        val classification: String,
        val repository: String,
        val GalleryNumber: String
    )

    private fun parseYear(dateString: String): Int? {
        return try {
            // Try to extract a year from various formats
            val yearPattern = "\\d{4}".toRegex()
            yearPattern.find(dateString)?.value?.toInt()
        } catch (e: Exception) {
            logger.debug("Could not parse year from date string: $dateString")
            null
        }
    }

    override suspend fun fetchRandomArtwork(): Result<Pair<Path, ArtworkMetadata>> = runCatching {
        // Select a random department for this fetch
        val selectedDepartment = departments.random()
        logger.info("Fetching random artwork from Met Museum API, Department ID: $selectedDepartment")
        
        // Get all object IDs for the selected department
        val objectIdsRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/objects?departmentIds=$selectedDepartment"))
            .build()
            
        val objectIdsResponse: HttpResponse<String> = client.send(objectIdsRequest, BodyHandlers.ofString())
        val objectIds = json.decodeFromString<ObjectIdsResponse>(objectIdsResponse.body()).objectIDs
        
        if (objectIds.isEmpty()) {
            throw IllegalStateException("No artworks found in the selected department")
        }

        // Filter out IDs that are in history
        val availableIds = objectIds.filter { !historyManager.isInHistory(it.toString()) }
        if (availableIds.isEmpty()) {
            logger.info("All artworks have been shown, clearing history")
            historyManager.clearHistory()
            return@runCatching fetchRandomArtwork().getOrThrow()
        }

        // Get random artwork ID
        val randomId = availableIds[Random.nextInt(availableIds.size)]
        
        // Fetch artwork details
        val artworkRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/objects/$randomId"))
            .build()
            
        val artworkResponse: HttpResponse<String> = client.send(artworkRequest, BodyHandlers.ofString())
        val artwork = json.decodeFromString<ArtworkResponse>(artworkResponse.body())
        
        if (artwork.primaryImage.isEmpty()) {
            logger.info("Artwork has no image, trying another one")
            return@runCatching fetchRandomArtwork().getOrThrow()
        }

        // Download the image
        val imageRequest = HttpRequest.newBuilder()
            .uri(URI.create(artwork.primaryImage))
            .build()
            
        val imageResponse: HttpResponse<ByteArray> = client.send(imageRequest, BodyHandlers.ofByteArray())
        
        // Add validation
        if (imageResponse.statusCode() != 200 || imageResponse.body().isEmpty()) {
            logger.error("Failed to download image for artwork ${artwork.objectID}")
            return@runCatching fetchRandomArtwork().getOrThrow()
        }

        // Save to temp file
        val tempFile = createTempFile(cacheDir, "artwork-${artwork.objectID}-", ".jpg")
        tempFile.writeBytes(imageResponse.body())

        // Validate the saved file
        if (!tempFile.exists() || tempFile.fileSize() == 0L) {
            logger.error("Failed to save image for artwork ${artwork.objectID}")
            tempFile.deleteIfExists()
            return@runCatching fetchRandomArtwork().getOrThrow()
        }

        try {
            // Process the image
            val imageProcessor = ImageProcessor()
            val processedFile = imageProcessor.processImage(tempFile)
            tempFile.deleteIfExists() // Clean up original file
            
            if (!processedFile.exists()) {
                logger.error("Image processing failed for artwork ${artwork.objectID}")
                return@runCatching fetchRandomArtwork().getOrThrow()
            }
            
            // Create metadata with more detailed information
            val metadata = ArtworkMetadata(
                id = artwork.objectID.toString(),
                title = artwork.title,
                artist = artwork.artistDisplayName.ifEmpty { "Unknown Artist" },
                year = if (artwork.objectBeginDate > 0) artwork.objectBeginDate else parseYear(artwork.objectDate),
                description = buildString {
                    append(artwork.medium)
                    if (artwork.dimensions.isNotEmpty()) append("\nDimensions: ${artwork.dimensions}")
                    if (artwork.culture.isNotEmpty()) append("\nCulture: ${artwork.culture}")
                    if (artwork.period.isNotEmpty()) append("\nPeriod: ${artwork.period}")
                    if (artwork.dynasty.isNotEmpty()) append("\nDynasty: ${artwork.dynasty}")
                    if (artwork.reign.isNotEmpty()) append("\nReign: ${artwork.reign}")
                    if (artwork.artistDisplayBio.isNotEmpty()) append("\nArtist: ${artwork.artistDisplayBio}")
                    if (artwork.artistNationality.isNotEmpty()) append(" (${artwork.artistNationality})")
                    if (artwork.GalleryNumber.isNotEmpty()) append("\nGallery: ${artwork.GalleryNumber}")
                    append("\nCollection: ${artwork.repository}")
                },
                source = "The Metropolitan Museum of Art",
                imageUrl = artwork.primaryImage,
                fetchedAt = System.currentTimeMillis()
            )

            logger.info("Successfully fetched artwork: ${metadata.title} by ${metadata.artist}")
            processedFile to metadata
        } catch (e: Exception) {
            logger.error("Failed to process image for artwork ${artwork.objectID}", e)
            tempFile.deleteIfExists()
            return@runCatching fetchRandomArtwork().getOrThrow()
        }
    }
} 