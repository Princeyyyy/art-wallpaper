// No package declaration — intentionally in default package to match existing imports.
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.serializer
import java.time.Duration
import kotlin.io.path.*

// Fix #11 (Medium): Mark Settings as @Immutable so Compose compiler skips unnecessary
// recompositions of deep composable subtrees when settings remain unchanged.
@androidx.compose.runtime.Immutable
@Serializable
enum class AppTheme {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Wallpaper presentation mode.
 * Ambient Blur Mat is the permanent, de facto museum standard across all displays.
 */
// Fix #11 (Medium): Mark Settings as @Immutable so Compose compiler skips unnecessary
// recompositions of deep composable subtrees when settings remain unchanged.
@androidx.compose.runtime.Immutable
@Serializable
enum class WallpaperFitMode {
    AMBIENT_BLUR
}

// Fix #11 (Medium): Mark Settings as @Immutable so Compose compiler skips unnecessary
// recompositions of deep composable subtrees when settings remain unchanged.
@androidx.compose.runtime.Immutable
@Serializable
data class Settings(
    val changeIntervalHours: Int = 24,
    val updateTimeHour: Int = 7,
    val updateTimeMinute: Int = 0,
    val startWithSystem: Boolean = true,
    val showNotifications: Boolean = true,
    val displayStyle: DisplayStyle = DisplayStyle.FILL,
    val offlineRotationStrategy: RotationStrategy = RotationStrategy.RANDOM,
    val hasEnabledAutoStart: Boolean = true,
    val isFirstRun: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val autoCheckUpdates: Boolean = true,
    val dismissedUpdateVersion: String? = null,
    val hasSetUpdateTime: Boolean = true,
    val themeMode: AppTheme = AppTheme.SYSTEM,
    val wallpaperFitMode: WallpaperFitMode = WallpaperFitMode.AMBIENT_BLUR,
    val syncLockScreen: Boolean = false
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
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }

        private val settingsPath = Path(System.getProperty("user.home"), ".artwallpaper", "settings.json")
        private val backupPath = Path(System.getProperty("user.home"), ".artwallpaper", "settings.json.bak")
        private val _currentSettings = MutableStateFlow<Settings>(Settings())
        val currentSettings: StateFlow<Settings> = _currentSettings.asStateFlow()

        private val logger = LoggerFactory.getLogger(Settings::class.java)

        fun updateCurrentSettings(settings: Settings) {
            _currentSettings.value = settings
            settings.save()
        }

        fun loadSavedSettings(): Settings {
            return try {
                if (settingsPath.exists()) {
                    val settings = json.decodeFromString<Settings>(settingsPath.readText())
                    _currentSettings.value = settings
                    settings
                } else if (backupPath.exists()) {
                    logger.info("Main settings file not found, attempting to restore from backup")
                    val settings = json.decodeFromString<Settings>(backupPath.readText())
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

    fun save() {
        try {
            backup()
            settingsPath.parent.createDirectories()
            val encoded = json.encodeToString(serializer(), this)
            val tempFile = settingsPath.resolveSibling("${settingsPath.fileName}.tmp")
            tempFile.writeText(encoded)
            try {
                java.nio.file.Files.move(
                    tempFile,
                    settingsPath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                java.nio.file.Files.move(
                    tempFile,
                    settingsPath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
            _currentSettings.value = this
        } catch (e: Exception) {
            logger.error("Failed to save settings", e)
            throw e
        }
    }
}
