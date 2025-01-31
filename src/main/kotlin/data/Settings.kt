import kotlinx.serialization.Serializable
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.createDirectories
import kotlinx.serialization.json.Json
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
data class Settings(
    val changeIntervalHours: Int = 24, // Fixed at 24 hours
    val startWithSystem: Boolean = true,
    val showNotifications: Boolean = true,
    val displayStyle: DisplayStyle = DisplayStyle.FILL,
    val offlineRotationStrategy: RotationStrategy = RotationStrategy.RANDOM,
    val hasEnabledAutoStart: Boolean = false,
    val isFirstRun: Boolean = true
) {
    @Serializable
    enum class DisplayStyle {
        FILL
    }

    companion object {
        private val settingsPath = Path(System.getProperty("user.home"), ".artwallpaper", "settings.json")

        fun load(): Settings {
            return try {
                if (settingsPath.exists()) {
                    Json.decodeFromString<Settings>(settingsPath.readText())
                } else {
                    Settings()
                }
            } catch (e: Exception) {
                Settings()
            }
        }
    }

    fun save() {
        settingsPath.parent.createDirectories()
        settingsPath.writeText(Json.encodeToString(Settings.serializer(), this))
    }
} 