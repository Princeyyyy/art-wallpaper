package service

import ArtworkMetadata
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.*
import org.slf4j.LoggerFactory

class HistoryManager(
    baseDir: Path = Path(System.getProperty("user.home"), ".artwallpaper")
) {
    private val logger = LoggerFactory.getLogger(HistoryManager::class.java)
    private val historyFile = baseDir.resolve("history.json")
    private val maxHistoryItems = 100

    init {
        baseDir.createDirectories()
        if (!historyFile.exists()) {
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
                kotlinx.serialization.builtins.ListSerializer(ArtworkMetadata.serializer()),
                trimmedHistory
            ))
        } catch (e: Exception) {
            logger.error("Failed to add to history", e)
        }
    }

    suspend fun getHistory(): List<ArtworkMetadata> = withContext(Dispatchers.IO) {
        try {
            Json.decodeFromString<List<ArtworkMetadata>>(historyFile.readText())
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
                kotlinx.serialization.builtins.ListSerializer(ArtworkMetadata.serializer()),
                history
            ))
        } catch (e: Exception) {
            logger.error("Failed to save history", e)
        }
    }
}