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
        private val backupPath = Path(System.getProperty("user.home"), ".artwallpaper", "settings.json.bak")
        private val _currentSettings = MutableStateFlow<Settings>(Settings())
        val currentSettings: StateFlow<Settings> = _currentSettings.asStateFlow()
        
        @kotlinx.serialization.Transient
        private val logger = LoggerFactory.getLogger(Settings::class.java)

        fun updateCurrentSettings(settings: Settings) {
            _currentSettings.value = settings
            settings.save()
        }

        fun loadSavedSettings(): Settings {
            return try {
                if (settingsPath.exists()) {
                    val settings = Json.decodeFromString<Settings>(settingsPath.readText())
                    _currentSettings.value = settings
                    settings
                } else if (backupPath.exists()) {
                    logger.info("Main settings file not found, attempting to restore from backup")
                    val settings = Json.decodeFromString<Settings>(backupPath.readText())
                    _currentSettings.value = settings
                    settings
                } else {
                    Settings()
                }
            } catch (e: Exception) {
                logger.error("Failed to load settings", e)
                Settings()
            }
        }

        private fun backup() {
            try {
                if (settingsPath.exists()) {
                    settingsPath.parent.createDirectories()
                    settingsPath.copyTo(backupPath, overwrite = true)
                }
            } catch (e: Exception) {
                logger.error("Failed to backup settings", e)
            }
        }

        init {
            loadSavedSettings()
        }
    }

    init {
        require(startWithSystem == hasEnabledAutoStart) {
            "startWithSystem and hasEnabledAutoStart must have the same value"
        }
    }

    fun save() {
        try {
            backup()
            settingsPath.parent.createDirectories()
            settingsPath.writeText(Json.encodeToString(serializer(), this))
            _currentSettings.value = this
        } catch (e: Exception) {
            logger.error("Failed to save settings", e)
            throw e
        }
    }
} 