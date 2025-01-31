import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class ArtworkStorageManager(
    baseDir: Path = Path.of(System.getProperty("user.home"), ".artwallpaper"),
    private val maxStoredArtworks: Int = 100  // Keep last 100 artworks
) {
    private val artworksDir = baseDir.resolve("artworks")
    private val metadataDir = baseDir.resolve("metadata")
    private var cachedArtworks: List<Pair<Path, ArtworkMetadata>>? = null
    private val logger = LoggerFactory.getLogger(ArtworkStorageManager::class.java)

    init {
        artworksDir.createDirectories()
        metadataDir.createDirectories()
    }

    fun saveArtwork(artwork: Path, metadata: ArtworkMetadata): Path {
        val targetPath = artworksDir.resolve("${metadata.id}.jpg")
        artwork.copyTo(targetPath, overwrite = true)
        
        // Save metadata
        metadataDir.resolve("${metadata.id}.json")
            .writeText(Json.encodeToString(metadata))
        
        cachedArtworks = null // Invalidate cache
        return targetPath
    }

    fun getStoredArtworks(): List<Pair<Path, ArtworkMetadata>> {
        return cachedArtworks ?: loadStoredArtworks().also { cachedArtworks = it }
    }

    private fun loadStoredArtworks(): List<Pair<Path, ArtworkMetadata>> {
        return artworksDir.listDirectoryEntries("*.jpg").mapNotNull { artworkPath ->
            val id = artworkPath.nameWithoutExtension
            val metadataPath = metadataDir.resolve("$id.json")
            
            if (metadataPath.exists()) {
                val metadata = Json.decodeFromString<ArtworkMetadata>(metadataPath.readText())
                artworkPath to metadata
            } else null
        }
    }

    fun reloadData() {
        cachedArtworks = null // Force reload on next access
    }

    fun getArtworkPath(id: String): Path? {
        val artworkPath = artworksDir.resolve("$id.jpg")
        return if (artworkPath.exists()) artworkPath else null
    }

    suspend fun cleanupStorage() = withContext(Dispatchers.IO) {
        try {
            val artworks = getStoredArtworks()
                .sortedByDescending { it.second.fetchedAt }

            if (artworks.size > maxStoredArtworks) {
                logger.info("Cleaning up storage, removing ${artworks.size - maxStoredArtworks} artworks")
                artworks.drop(maxStoredArtworks).forEach { (path, _) ->
                    path.deleteIfExists()
                    // Delete associated metadata file
                    getMetadataPath(path).deleteIfExists()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to clean up storage", e)
        }
    }

    private fun getMetadataPath(artworkPath: Path): Path {
        return metadataDir.resolve("${artworkPath.nameWithoutExtension}.json")
    }
} 