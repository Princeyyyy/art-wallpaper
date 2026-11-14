import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.*
import org.slf4j.LoggerFactory

class ArtworkStorageManager(
    baseDir: Path = Path.of(System.getProperty("user.home"), ".artwallpaper"),
    private val maxStoredArtworks: Int = 20  // Lean cache: keep last 20 artworks to save disk space
) {
    private val artworksDir = baseDir.resolve("artworks")
    private val metadataDir = baseDir.resolve("metadata")
    // Fix #1 (Critical): @Volatile ensures cross-thread visibility; synchronized double-checked
    // locking serializes concurrent cache rebuilds without lock overhead on cache hits.
    @Volatile private var cachedArtworks: List<Pair<Path, ArtworkMetadata>>? = null
    private val logger = LoggerFactory.getLogger(ArtworkStorageManager::class.java)

    init {
        artworksDir.createDirectories()
        metadataDir.createDirectories()
    }

    fun getArtworksDir(): Path = artworksDir

    fun saveArtwork(artwork: Path, metadata: ArtworkMetadata): Path {
        val targetPath = artworksDir.resolve("${metadata.id}.jpg")
        if (artwork.toAbsolutePath().normalize() != targetPath.toAbsolutePath().normalize()) {
            artwork.copyTo(targetPath, overwrite = true)
        } else {
            runCatching {
                java.nio.file.Files.setLastModifiedTime(
                    targetPath,
                    java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis())
                )
            }
        }
        
        // Save metadata
        metadataDir.resolve("${metadata.id}.json")
            .writeText(Json.encodeToString(metadata))
        
        synchronized(this) {
            cachedArtworks = null
        }
        cleanup()
        return targetPath
    }

    fun getStoredArtworks(): List<Pair<Path, ArtworkMetadata>> {
        return cachedArtworks ?: synchronized(this) {
            cachedArtworks ?: loadStoredArtworks().also { cachedArtworks = it }
        }
    }

    fun getLatestStoredArtwork(): Pair<Path, ArtworkMetadata>? {
        return getStoredArtworks().firstOrNull()
    }

    fun getArtworkMetadata(id: String): ArtworkMetadata? {
        val metadataPath = metadataDir.resolve("$id.json")
        return if (metadataPath.exists()) {
            try {
                Json.decodeFromString<ArtworkMetadata>(metadataPath.readText())
            } catch (e: Exception) {
                logger.warn("Skipping corrupt metadata at $metadataPath: ${e.message}")
                null
            }
        } else null
    }

    private fun loadStoredArtworks(): List<Pair<Path, ArtworkMetadata>> {
        return artworksDir.listDirectoryEntries("*.jpg")
            .sortedByDescending {
                try { it.getLastModifiedTime().toMillis() } catch (_: Exception) { 0L }
            }
            .mapNotNull { artworkPath ->
                val id = artworkPath.nameWithoutExtension
                val metadata = getArtworkMetadata(id)
                if (metadata != null) artworkPath to metadata else null
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
                
                synchronized(this) {
                    cachedArtworks = null // Invalidate cache
                }
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