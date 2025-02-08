import kotlinx.serialization.Serializable
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.createDirectories
import kotlinx.serialization.json.Json
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.slf4j.LoggerFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.serializer

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
                    Json.decodeFromString<Settings>(settingsPath.readText()).also {
                        logger.info("Settings loaded: notificationsEnabled=${it.notificationsEnabled}")
                        _currentSettings.value = it
                    }
                } else {
                    Settings().also {
                        logger.info("Using default settings: notificationsEnabled=${it.notificationsEnabled}")
                        _currentSettings.value = it
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to load settings, using defaults", e)
                Settings().also { _currentSettings.value = it }
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
            settingsPath.parent.createDirectories()
            val json = Json { 
                prettyPrint = true 
                encodeDefaults = true
            }
            val settingsToSave = if (startWithSystem != hasEnabledAutoStart) {
                this.copy(hasEnabledAutoStart = startWithSystem)
            } else {
                this
            }
            settingsPath.writeText(json.encodeToString(serializer(), settingsToSave))
            _currentSettings.value = settingsToSave
            logger.info("Settings saved: updateTimeHour=$updateTimeHour, updateTimeMinute=$updateTimeMinute, hasSetUpdateTime=$hasSetUpdateTime, startWithSystem=$startWithSystem, hasEnabledAutoStart=$hasEnabledAutoStart")
        } catch (e: Exception) {
            logger.error("Failed to save settings", e)
        }
    }
} 