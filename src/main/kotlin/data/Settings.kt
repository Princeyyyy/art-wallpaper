import kotlinx.serialization.Serializable
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.createDirectories
import kotlinx.serialization.json.Json
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.slf4j.LoggerFactory

@Serializable
data class Settings(
    val changeIntervalHours: Int = 24, // Fixed at 24 hours
    val startWithSystem: Boolean = true,
    val showNotifications: Boolean = true,
    val displayStyle: DisplayStyle = DisplayStyle.FILL,
    val offlineRotationStrategy: RotationStrategy = RotationStrategy.RANDOM,
    val hasEnabledAutoStart: Boolean = false,
    val isFirstRun: Boolean = true,
    val notificationsEnabled: Boolean = true
) {
    private val logger = LoggerFactory.getLogger(Settings::class.java)

    @Serializable
    enum class DisplayStyle {
        FILL
    }

    @Serializable
    enum class RotationStrategy {
        RANDOM
    }

    companion object {
        private val settingsPath = Path(System.getProperty("user.home"), ".artwallpaper", "settings.json")
        private val logger = LoggerFactory.getLogger(Settings::class.java)

        fun load(): Settings {
            return try {
                if (settingsPath.exists()) {
                    Json.decodeFromString<Settings>(settingsPath.readText()).also {
                        logger.info("Settings loaded: notificationsEnabled=${it.notificationsEnabled}")
                    }
                } else {
                    Settings().also {
                        logger.info("Using default settings: notificationsEnabled=${it.notificationsEnabled}")
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to load settings, using defaults", e)
                Settings()
            }
        }
    }

    fun save() {
        try {
            settingsPath.parent.createDirectories()
            val json = Json { prettyPrint = true }
            settingsPath.writeText(json.encodeToString(serializer(), this))
            logger.info("Settings saved: notificationsEnabled=$notificationsEnabled, isFirstRun=$isFirstRun, hasEnabledAutoStart=$hasEnabledAutoStart")
        } catch (e: Exception) {
            logger.error("Failed to save settings", e)
        }
    }
} 