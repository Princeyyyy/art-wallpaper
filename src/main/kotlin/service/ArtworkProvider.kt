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
    // FIX #7: artworksDir is now used by getRandomCachedArtwork() (was silently ignored before).
    private val artworksDir: Path = Path.of(System.getProperty("user.home"), ".artwallpaper", "artworks"),
    private val connectivityChecker: ConnectivityChecker = ConnectivityChecker(),
    historyManager: HistoryManager,
    private val maxRetries: Int = 3
) {
    private val logger = LoggerFactory.getLogger(ArtworkProvider::class.java)
    private val source: ArtworkSource = MetMuseumSource(client, cacheDir, historyManager)

    init {
        cacheDir.createDirectories()
        artworksDir.createDirectories()
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

    // FIX #17: Use artworksDir (the permanent store) for offline fallback, not cacheDir.
    // Also look for processed_ prefixed files that ImageProcessor actually produces.
    private fun getRandomCachedArtwork(): Result<Pair<Path, ArtworkMetadata>> = runCatching {
        // Prefer the permanent artworks directory first.
        val artworkFiles = artworksDir.listDirectoryEntries("*.jpg")
            .ifEmpty { cacheDir.listDirectoryEntries("processed_*.jpg") }

        if (artworkFiles.isEmpty()) {
            throw IllegalStateException("No cached artworks available")
        }

        val artworkPath = artworkFiles.random()
        val id = artworkPath.nameWithoutExtension

        // Look for metadata next to the artwork file, then in cacheDir.
        val metadataPath = artworksDir.parent.resolve("metadata").resolve("$id.json")
            .takeIf { it.exists() }
            ?: cacheDir.resolve("processed_artwork-$id.json")
                .takeIf { it.exists() }

        if (metadataPath == null) {
            throw IllegalStateException("Metadata not found for cached artwork: $id")
        }

        val metadata = Json { ignoreUnknownKeys = true }
            .decodeFromString<ArtworkMetadata>(metadataPath.readText())
        artworkPath to metadata
    }

    fun cleanupCacheFiles(exceptId: String? = null) {
        try {
            logger.info("Starting cache cleanup")
            cacheDir.listDirectoryEntries().forEach { file ->
                try {
                    val artworkId = when {
                        file.name.startsWith("processed_artwork-") -> {
                            file.name.substringAfter("processed_artwork-")
                                .substringBefore("-")
                        }
                        else -> file.nameWithoutExtension.substringBefore('.')
                    }

                    if (exceptId != null && artworkId == exceptId) {
                        logger.debug("Skipping cleanup of current artwork: ${file.name}")
                        return@forEach
                    }

                    val isStoredInArtworks = artworksDir.resolve("$artworkId.jpg").exists()

                    if (isStoredInArtworks || exceptId == null) {
                        file.deleteIfExists()
                        cacheDir.resolve("processed_artwork-$artworkId.json").deleteIfExists()
                        logger.info("Cleaned up cache file: ${file.name}")
                    }
                } catch (e: Exception) {
                    logger.error("Failed to delete cache file: ${file.name}", e)
                }
            }
            logger.info("Cache cleanup completed")
        } catch (e: Exception) {
            logger.error("Failed to cleanup cache files", e)
        }
    }
}
