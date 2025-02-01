import data.ArtworkSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import service.HistoryManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path

class UnsplashSource(
    private val client: HttpClient,
    private val cacheDir: Path,
    private val historyManager: HistoryManager
) : ArtworkSource {
    private val logger = LoggerFactory.getLogger(UnsplashSource::class.java)
    private val apiKey = "9s14SuAa58HNb3SIChKeEZe8YL1wlYguwlfmWZ4XCMA" // Need to register at https://unsplash.com/developers
    private val baseUrl = "https://api.unsplash.com"
    private val imageProcessor = ImageProcessor()

    override suspend fun fetchRandomArtwork(): Result<Pair<Path, ArtworkMetadata>> = runCatching {
        withContext(Dispatchers.IO) {
            logger.info("Fetching random artwork from Unsplash")
            
            // Get list of recently used IDs from history
            val recentIds = historyManager.getHistory()
                .take(50).joinToString(",") { it.id }

            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/photos/random?" + 
                    "query=art,artwork,painting" +
                    "&orientation=landscape" +
                    "&content_filter=high" +
                    "&featured=true" + // Get curated photos
                    (if (recentIds.isNotEmpty()) "&exclude=$recentIds" else "") // Exclude recent photos
                ))
                .header("Authorization", "Client-ID $apiKey")
                .header("Accept-Version", "v1")
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                throw IllegalStateException("Failed to fetch artwork: HTTP ${response.statusCode()}")
            }

            val json = Json.parseToJsonElement(response.body()).jsonObject
            val artwork = parseArtworkData(json)
            val originalPath = cacheDir.resolve("unsplash_${artwork.id}.jpg")
            
            downloadImage(artwork.imageUrl, originalPath)
            val processedPath = imageProcessor.processImage(originalPath)
            
            // Return the processed image and metadata without cleaning up yet
            processedPath to artwork
        }
    }.onFailure { error ->
        logger.error("Failed to fetch artwork from Unsplash", error)
    }

    private fun parseArtworkData(json: JsonObject): ArtworkMetadata {
        val id = json["id"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("No ID in artwork data")
            
        val urls = json["urls"]?.jsonObject
            ?: throw IllegalStateException("No URLs in artwork data")
            
        val user = json["user"]?.jsonObject
            ?: throw IllegalStateException("No user data in artwork")

        return ArtworkMetadata(
            id = id,
            title = json["description"]?.jsonPrimitive?.contentOrNull 
                ?: json["alt_description"]?.jsonPrimitive?.contentOrNull 
                ?: "Untitled",
            artist = user["name"]?.jsonPrimitive?.contentOrNull,
            year = null,
            description = "Photo by ${user["name"]?.jsonPrimitive?.contentOrNull} on Unsplash",
            source = "Unsplash",
            imageUrl = urls["raw"]?.jsonPrimitive?.contentOrNull 
                ?: throw IllegalStateException("No image URL in artwork data"),
            fetchedAt = System.currentTimeMillis()
        )
    }

    private suspend fun downloadImage(url: String, targetPath: Path) {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()
            
        val response = withContext(Dispatchers.IO) {
            client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        }
        if (response.statusCode() != 200) {
            throw IllegalStateException("Failed to download image: HTTP ${response.statusCode()}")
        }
        
        targetPath.toFile().writeBytes(response.body())
    }

    override fun getSourceName() = "Unsplash"
    
    override fun getArtworkUrl(artworkId: String): String =
        "https://unsplash.com/photos/$artworkId"
}