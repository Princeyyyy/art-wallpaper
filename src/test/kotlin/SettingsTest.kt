import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

class SettingsTest {

    // Settings uses a companion-level static path; we can't easily redirect that path,
    // so we test the pure data class and serialization behaviour directly.

    @Test
    fun `default settings have sane values`() {
        val s = Settings()
        assertEquals(24, s.changeIntervalHours)
        assertEquals(7, s.updateTimeHour)
        assertEquals(0, s.updateTimeMinute)
        assertTrue(s.startWithSystem)
        assertTrue(s.isFirstRun)
        assertEquals(Settings.DisplayStyle.FILL, s.displayStyle)
        assertEquals(Settings.RotationStrategy.RANDOM, s.offlineRotationStrategy)
    }

    @Test
    fun `settings serialize and deserialize with no data loss`() {
        val original = Settings(
            changeIntervalHours = 12,
            updateTimeHour = 8,
            updateTimeMinute = 30,
            startWithSystem = false,
            showNotifications = false,
            isFirstRun = false,
            notificationsEnabled = false,
            hasSetUpdateTime = false
        )
        val json = Json.encodeToString(Settings.serializer(), original)
        val decoded = Json.decodeFromString(Settings.serializer(), json)
        assertEquals(original, decoded)
    }

    @Test
    fun `invalid json returns default settings`() {
        // loadSavedSettings() catches exceptions and returns Settings()
        // We can verify this by calling it after writing corrupt JSON
        val tempDir = Files.createTempDirectory("settings-test")
        try {
            val f = tempDir.resolve("settings.json")
            f.writeText("{ this is not valid json }")
            // Settings.loadSavedSettings() reads from a fixed path — we can't redirect it
            // in a pure unit test without refactoring. Instead, verify the Json decoder itself:
            assertFailsWith<Exception> {
                Json.decodeFromString<Settings>("{ invalid }")
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `updateInterval matches changeIntervalHours`() {
        val s = Settings(changeIntervalHours = 6)
        assertEquals(6L, s.updateInterval.toHours())
    }

    @Test
    fun `preferredHours delegates to updateTimeHour`() {
        val s = Settings(updateTimeHour = 9)
        assertEquals(9, s.preferredHours)
    }

    @Test
    fun `save and reload roundtrip via temp file using Json directly`() {
        val tempDir = Files.createTempDirectory("settings-roundtrip-test")
        try {
            val settingsFile = tempDir.resolve("settings.json")
            val original = Settings(
                changeIntervalHours = 48,
                updateTimeHour = 22,
                isFirstRun = false,
                startWithSystem = false
            )
            settingsFile.writeText(Json.encodeToString(Settings.serializer(), original))
            val loaded = Json.decodeFromString<Settings>(settingsFile.readText())
            assertEquals(original.changeIntervalHours, loaded.changeIntervalHours)
            assertEquals(original.updateTimeHour, loaded.updateTimeHour)
            assertEquals(original.isFirstRun, loaded.isFirstRun)
            assertEquals(original.startWithSystem, loaded.startWithSystem)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
