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

    fun getArtworksDir(): Path = artworksDir

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

    fun getArtworkPath(id: String): Path? {
        val artworkPath = artworksDir.resolve("$id.jpg")
        return if (artworkPath.exists()) artworkPath else null
    }

    fun cleanup() {
        try {
            val artworks = getStoredArtworks()
            if (artworks.size > maxStoredArtworks) {
                // Sort by last modified time and keep only the most recent ones
                val toDelete = artworks
                    .sortedByDescending { it.first.getLastModifiedTime() }
                    .drop(maxStoredArtworks)
                
                toDelete.forEach { (path, metadata) ->
                    path.deleteIfExists()
                    metadataDir.resolve("${metadata.id}.json").deleteIfExists()
                    logger.info("Cleaned up old artwork: ${metadata.title}")
                }
                
                cachedArtworks = null // Invalidate cache
            }
        } catch (e: Exception) {
            logger.error("Failed to cleanup old artworks", e)
        }
    }

    fun cleanupOldArtworks() {
        try {
            // Keep track of valid artwork IDs from metadata
            val validIds = metadataDir.listDirectoryEntries("*.json")
                .map { it.nameWithoutExtension }
                .toSet()

            // Clean up orphaned artwork files
            artworksDir.listDirectoryEntries("*.jpg").forEach { artworkPath ->
                val id = artworkPath.nameWithoutExtension
                if (id !in validIds) {
                    artworkPath.deleteIfExists()
                    logger.info("Cleaned up orphaned artwork file: ${artworkPath.fileName}")
                }
            }

            // Clean up orphaned metadata files
            val validArtworkIds = artworksDir.listDirectoryEntries("*.jpg")
                .map { it.nameWithoutExtension }
                .toSet()

            metadataDir.listDirectoryEntries("*.json").forEach { metadataPath ->
                val id = metadataPath.nameWithoutExtension
                if (id !in validArtworkIds) {
                    metadataPath.deleteIfExists()
                    logger.info("Cleaned up orphaned metadata file: ${metadataPath.fileName}")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to cleanup old artworks", e)
        }
    }

    private fun getMetadataPath(artworkPath: Path): Path {
        return metadataDir.resolve("${artworkPath.nameWithoutExtension}.json")
    }
} 