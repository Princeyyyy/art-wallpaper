import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.serializer
import java.time.Duration
import kotlin.io.path.*

@Serializable
data class Settings(
    val changeIntervalHours: Int = 24, // Fixed at 24 hours
    val updateTimeHour: Int = 7, // Default to 7 AM
    val updateTimeMinute: Int = 0,
    val startWithSystem: Boolean = true,
    val showNotifications: Boolean = true,
    val displayStyle: DisplayStyle = DisplayStyle.FILL,
    val offlineRotationStrategy: RotationStrategy = RotationStrategy.RANDOM,
    val hasEnabledAutoStart: Boolean = true,
    val isFirstRun: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val hasSetUpdateTime: Boolean = true  // Changed to true since we have a default time
) {
    @kotlinx.serialization.Transient
    private val logger = LoggerFactory.getLogger(Settings::class.java)

    val updateInterval: Duration
        get() = Duration.ofHours(changeIntervalHours.toLong())

    val preferredHours: Int
        get() = updateTimeHour

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
        private val _currentSettings = MutableStateFlow<Settings>(Settings())
        val currentSettings: StateFlow<Settings> = _currentSettings.asStateFlow()
        
        @kotlinx.serialization.Transient
        private val logger = LoggerFactory.getLogger(Settings::class.java)

        fun load(): Settings {
            return try {
                if (settingsPath.exists()) {
                    Json.decodeFromString<Settings>(settingsPath.readText())
                } else {
                    val defaultSettings = Settings()
                    defaultSettings.save()
                    defaultSettings
                }
            } catch (e: Exception) {
                logger.error("Failed to load settings, using defaults", e)
                Settings()
            }
        }

        fun backup() {
            try {
                val backupPath = settingsPath.resolveSibling("settings.backup.json")
                if (settingsPath.exists()) {
                    settingsPath.copyTo(backupPath, overwrite = true)
                }
            } catch (e: Exception) {
                logger.error("Failed to backup settings", e)
            }
        }
    }

    init {
        require(startWithSystem == hasEnabledAutoStart) {
            "startWithSystem and hasEnabledAutoStart must have the same value"
        }
    }

    fun save() {
        try {
            Settings.backup()
            settingsPath.parent.createDirectories()
            settingsPath.writeText(Json.encodeToString(serializer(), this))
            _currentSettings.value = this
        } catch (e: Exception) {
            logger.error("Failed to save settings", e)
            throw e
        }
    }
} 