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
    // FIX #1: Store IDs (Strings), not full ArtworkMetadata objects.
    // This is consistent with isInHistory(), clearHistory(), and saveHistory().
    private val history = mutableSetOf<String>()

    init {
        baseDir.createDirectories()
        if (!historyFile.exists()) {
            historyFile.writeText("[]")
        }
        loadHistory()
    }

    // FIX #1: Actually populate the in-memory Set from disk.
    // Previously the decoded list was silently discarded.
    private fun loadHistory() {
        try {
            if (historyFile.exists()) {
                val content = historyFile.readText()
                if (content.isNotEmpty() && content != "[]") {
                    val ids = try {
                        // Preferred format: plain list of ID strings
                        Json.decodeFromString(ListSerializer(String.serializer()), content)
                    } catch (_: Exception) {
                        // Legacy migration: file may contain ArtworkMetadata objects â€”
                        // extract just the IDs so we don't lose history after upgrade.
                        try {
                            Json { ignoreUnknownKeys = true }
                                .decodeFromString(ListSerializer(ArtworkMetadata.serializer()), content)
                                .map { it.id }
                        } catch (e: Exception) {
                            logger.warn("History file unparseable or corrupted, starting fresh: ${e.message}")
                            emptyList()
                        }
                    }
                    history.addAll(ids)
                    logger.info("Loaded ${history.size} items from history")
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to load history, resetting file: ${e.message}")
            historyFile.writeText("[]")
        }
    }

    suspend fun addToHistory(metadata: ArtworkMetadata) = withContext(Dispatchers.IO) {
        synchronized(this@HistoryManager) {
        try {
            history.add(metadata.id)
            // Keep only the most recent N items â€” trim oldest if needed
            if (history.size > maxHistoryItems) {
                val trimmed = history.toMutableList()
                    .takeLast(maxHistoryItems)
                history.clear()
                history.addAll(trimmed)
            }
            saveHistory()
        } catch (e: Exception) {
            logger.error("Failed to add to history", e)
            }
        }
    }

    suspend fun cleanupHistory(maxEntries: Int) = withContext(Dispatchers.IO) {
        if (history.size > maxEntries) {
            val trimmed = history.toMutableList().takeLast(maxEntries)
            history.clear()
            history.addAll(trimmed)
            saveHistory()
            logger.info("Cleaned up history, keeping last $maxEntries entries")
        }
    }

    fun isInHistory(id: String): Boolean = synchronized(this) { history.contains(id) }

    fun getHistory(): List<String> = synchronized(this) { history.toList() }

    fun clearHistory() {
        logger.info("Clearing artwork history")
        history.clear()
        saveHistory()
    }

    private fun saveHistory() {
        try {
            historyFile.parent.createDirectories()
            historyFile.writeText(
                Json.encodeToString(ListSerializer(String.serializer()), history.toList())
            )
            logger.debug("Saved ${history.size} items to history")
        } catch (e: Exception) {
            logger.error("Failed to save history", e)
        }
    }
}
