import data.ArtworkSource
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.*
import org.slf4j.LoggerFactory
import service.ConnectivityChecker
import service.HistoryManager
import java.net.http.HttpClient
import java.time.Duration

class ArtworkProvider(
    client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build(),
    private val cacheDir: Path = Path.of(System.getProperty("user.home"), ".artwallpaper", "cache"),
    private val connectivityChecker: ConnectivityChecker = ConnectivityChecker(),
    historyManager: HistoryManager,
    private val maxRetries: Int = 3
) {
    private val logger = LoggerFactory.getLogger(ArtworkProvider::class.java)
    private val source: ArtworkSource = MetMuseumSource(client, cacheDir, historyManager)

    init {
        cacheDir.createDirectories()
    }

    suspend fun fetchArtwork(): Result<Pair<Path, ArtworkMetadata>> = runCatching {
        if (!connectivityChecker.isNetworkAvailable()) {
            logger.info("Offline mode - using cached artwork")
            return@runCatching getRandomCachedArtwork().getOrThrow()
        }

        var lastError: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return@runCatching source.fetchRandomArtwork().getOrThrow()
            } catch (e: Exception) {
                logger.error("Attempt ${attempt + 1} failed", e)
                lastError = e
            }
        }
        
        throw lastError ?: IllegalStateException("Failed to fetch artwork after $maxRetries attempts")
    }

    private fun getRandomCachedArtwork(): Result<Pair<Path, ArtworkMetadata>> = runCatching {
        val cachedFiles = cacheDir.listDirectoryEntries("*.jpg")
        if (cachedFiles.isEmpty()) {
            throw IllegalStateException("No cached artworks available")
        }
        
        val artworkPath = cachedFiles.random()
        val metadataPath = cacheDir.resolve("${artworkPath.nameWithoutExtension}.json")
        
        if (!metadataPath.exists()) {
            throw IllegalStateException("Metadata not found for cached artwork")
        }
        
        val metadata = Json.decodeFromString<ArtworkMetadata>(metadataPath.readText())
        artworkPath to metadata
    }

    fun cleanupCacheFiles(exceptId: String? = null) {
        try {
            cacheDir.listDirectoryEntries().forEach { file ->
                if (exceptId == null || !file.name.startsWith(exceptId)) {
                    file.deleteIfExists()
                    logger.info("Cleaned up cache file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to cleanup cache files", e)
        }
    }
} 