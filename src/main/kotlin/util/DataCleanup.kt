package util

import kotlin.io.path.*
import java.nio.file.Path
import org.slf4j.LoggerFactory

object DataCleanup {
    private val logger = LoggerFactory.getLogger(DataCleanup::class.java)
    private val appDir = Path(System.getProperty("user.home"), ".artwallpaper")

    fun clearAllData() {
        try {
            if (appDir.exists()) {
                // Only clear files inside cache directory, keep the directory
                val cacheDir = appDir.resolve("cache")
                if (cacheDir.exists()) {
                    cacheDir.listDirectoryEntries().forEach { file ->
                        try {
                            file.deleteIfExists()
                            logger.info("Deleted cache file: ${file.fileName}")
                        } catch (e: Exception) {
                            logger.error("Failed to delete cache file: ${file.fileName}", e)
                        }
                    }
                    logger.info("Successfully cleared cache files")
                }
            }
        } catch (e: Exception) {
            logger.error("Error clearing cache files", e)
            throw e
        }
    }
} 