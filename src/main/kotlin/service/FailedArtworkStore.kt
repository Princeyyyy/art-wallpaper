package service

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Persists artwork IDs that returned permanently unusable data (missing image URL, HTTP 404/410,
 * empty payload, or corrupt image) so subsequent fetches never waste time retrying them.
 *
 * Transient network/connectivity errors must NOT be recorded here.
 */
class FailedArtworkStore(
    private val storeFile: Path = Path.of(System.getProperty("user.home"), ".artwallpaper", "failed_artworks.json"),
    private val maxEntries: Int = 2000
) {
    private val logger = LoggerFactory.getLogger(FailedArtworkStore::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val failedLookup = ConcurrentHashMap.newKeySet<String>()
    private val orderedIds = LinkedHashSet<String>()

    init {
        load()
    }

    private fun load() {
        try {
            if (!storeFile.exists()) return
            val content = storeFile.readText()
            if (content.isBlank() || content == "[]") return
            val ids = json.decodeFromString(ListSerializer(String.serializer()), content)
            synchronized(this) {
                val trimmed = if (ids.size > maxEntries) ids.takeLast(maxEntries) else ids
                orderedIds.addAll(trimmed)
                failedLookup.addAll(trimmed)
            }
            logger.debug("Loaded ${failedLookup.size} failed artwork IDs")
        } catch (e: Exception) {
            logger.warn("Failed artwork store unparseable, resetting: ${e.message}")
        }
    }

    fun isFailed(id: String): Boolean = failedLookup.contains(id)

    fun markFailed(id: String) {
        markFailedBatch(listOf(id))
    }

    fun markFailedBatch(ids: Collection<String>) {
        if (ids.isEmpty()) return
        var changed = false
        synchronized(this) {
            for (id in ids) {
                if (id.isNotBlank() && orderedIds.add(id)) {
                    failedLookup.add(id)
                    changed = true
                }
            }
            if (!changed) return
            while (orderedIds.size > maxEntries) {
                val eldest = orderedIds.first()
                orderedIds.remove(eldest)
                failedLookup.remove(eldest)
            }
            persist()
        }
    }

    fun size(): Int = failedLookup.size

    fun clear() {
        synchronized(this) {
            orderedIds.clear()
            failedLookup.clear()
            persist()
        }
    }

    private fun persist() {
        try {
            storeFile.parent?.createDirectories()
            storeFile.writeText(json.encodeToString(ListSerializer(String.serializer()), orderedIds.toList()))
        } catch (e: Exception) {
            logger.warn("Could not persist failed artwork IDs: ${e.message}")
        }
    }
}
