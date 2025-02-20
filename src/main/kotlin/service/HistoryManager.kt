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
    private val history = mutableSetOf<String>()

    init {
        baseDir.createDirectories()
        if (!historyFile.exists()) {
            historyFile.writeText("[]")
        }
        loadHistory()
    }

    private fun loadHistory() {
        try {
            if (historyFile.exists()) {
                val content = historyFile.readText()
                if (content.isNotEmpty()) {
                    Json.decodeFromString<List<ArtworkMetadata>>(content)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to load history, resetting file", e)
            historyFile.writeText("[]")
        }
    }

    suspend fun addToHistory(metadata: ArtworkMetadata) = withContext(Dispatchers.IO) {
        try {
            val history = getHistory().toMutableList()
            history.add(0, metadata)
            
            // Keep only last N items
            val trimmedHistory = history.take(maxHistoryItems)
            
            historyFile.writeText(Json.encodeToString(
                ListSerializer(ArtworkMetadata.serializer()),
                trimmedHistory
            ))
        } catch (e: Exception) {
            logger.error("Failed to add to history", e)
        }
    }

    private suspend fun getHistory(): List<ArtworkMetadata> = withContext(Dispatchers.IO) {
        try {
            val content = historyFile.readText()
            if (content.isBlank()) return@withContext emptyList()
            Json.decodeFromString(ListSerializer(ArtworkMetadata.serializer()), content)
        } catch (e: Exception) {
            logger.error("Failed to read history", e)
            emptyList()
        }
    }

    suspend fun cleanupHistory(maxEntries: Int) {
        val history = getHistory()
        if (history.size > maxEntries) {
            saveHistory(history.take(maxEntries))
            logger.info("Cleaned up history, keeping last $maxEntries entries")
        }
    }

    private suspend fun saveHistory(history: List<ArtworkMetadata>) = withContext(Dispatchers.IO) {
        try {
            historyFile.writeText(Json.encodeToString(
                ListSerializer(ArtworkMetadata.serializer()),
                history
            ))
        } catch (e: Exception) {
            logger.error("Failed to save history", e)
        }
    }

    fun isInHistory(id: String): Boolean {
        return history.contains(id)
    }

    fun clearHistory() {
        logger.info("Clearing artwork history")
        history.clear()
        saveHistory()
    }

    private fun saveHistory() {
        try {
            historyFile.parent.createDirectories()
            historyFile.writeText(Json.encodeToString(
                ListSerializer(String.serializer()),
                history.toList()
            ))
            logger.info("Saved ${history.size} items to history")
        } catch (e: Exception) {
            logger.error("Failed to save history", e)
        }
    }
}