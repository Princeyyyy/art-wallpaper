package service

import ArtworkMetadata
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.*
import org.slf4j.LoggerFactory
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

class HistoryManager(
    baseDir: Path = Path(System.getProperty("user.home"), ".artwallpaper")
) {
    private val logger = LoggerFactory.getLogger(HistoryManager::class.java)
    private val historyFile = baseDir.resolve("history.json")
    private val maxHistoryItems = 100
    private val json = Json { ignoreUnknownKeys = true }
    // Store IDs (Strings), not full ArtworkMetadata objects.
    private val historyIds = LinkedHashSet<String>()

    init {
        baseDir.createDirectories()
        if (!historyFile.exists()) {
            historyFile.writeText("[]")
        }
        loadHistory()
    }

    private fun loadHistory() {
        synchronized(this) {
            try {
                if (historyFile.exists()) {
                    val content = historyFile.readText()
                    if (content.isNotEmpty() && content != "[]") {
                        val ids = try {
                            json.decodeFromString(ListSerializer(String.serializer()), content)
                        } catch (_: Exception) {
                            try {
                                json.decodeFromString(ListSerializer(ArtworkMetadata.serializer()), content)
                                    .map { it.id }
                            } catch (e: Exception) {
                                logger.warn("History file unparseable or corrupted, starting fresh: ${e.message}")
                                emptyList()
                            }
                        }
                        historyIds.addAll(ids)
                        logger.info("Loaded ${historyIds.size} items from history")
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to load history, resetting file: ${e.message}")
                historyFile.writeText("[]")
            }
        }
    }

    suspend fun addToHistory(metadata: ArtworkMetadata) = withContext(Dispatchers.IO) {
        synchronized(this@HistoryManager) {
            try {
                historyIds.remove(metadata.id)
                historyIds.add(metadata.id)
                while (historyIds.size > maxHistoryItems) {
                    val eldest = historyIds.iterator().next()
                    historyIds.remove(eldest)
                }
                saveHistory()
            } catch (e: Exception) {
                logger.error("Failed to add to history", e)
            }
        }
    }

    suspend fun cleanupHistory(maxEntries: Int) = withContext(Dispatchers.IO) {
        synchronized(this@HistoryManager) {
            if (historyIds.size > maxEntries) {
                while (historyIds.size > maxEntries && historyIds.isNotEmpty()) {
                    val eldest = historyIds.iterator().next()
                    historyIds.remove(eldest)
                }
                saveHistory()
                logger.info("Cleaned up history, keeping last $maxEntries entries")
            }
        }
    }

    fun isInHistory(id: String): Boolean = synchronized(this) { historyIds.contains(id) }

    fun getHistory(): List<String> = synchronized(this) { historyIds.toList() }

    fun clearHistory() = synchronized(this) {
        logger.info("Clearing artwork history")
        historyIds.clear()
        saveHistory()
    }

    private fun saveHistory() {
        try {
            historyFile.parent.createDirectories()
            historyFile.writeText(
                json.encodeToString(ListSerializer(String.serializer()), historyIds.toList())
            )
            logger.debug("Saved ${historyIds.size} items to history")
        } catch (e: Exception) {
            logger.error("Failed to save history", e)
        }
    }
}
