import kotlinx.serialization.json.Json
import service.GithubAsset
import service.GithubRelease
import service.UpdateManager
import service.UpdateStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateManagerTest {

    @Test
    fun `isNewerVersion accurately compares semver strings`() {
        assertTrue(UpdateManager.isNewerVersion("4.0.3", "4.0.2"))
        assertTrue(UpdateManager.isNewerVersion("4.1.0", "4.0.2"))
        assertTrue(UpdateManager.isNewerVersion("5.0.0", "4.0.2"))
        assertTrue(UpdateManager.isNewerVersion("v4.0.3", "4.0.2"))
        assertTrue(UpdateManager.isNewerVersion("V4.1.0", "v4.0.2"))

        assertFalse(UpdateManager.isNewerVersion("4.0.2", "4.0.2"))
        assertFalse(UpdateManager.isNewerVersion("4.0.1", "4.0.2"))
        assertFalse(UpdateManager.isNewerVersion("3.9.9", "4.0.2"))
        assertFalse(UpdateManager.isNewerVersion("v4.0.2", "4.0.2"))
    }

    @Test
    fun `github release JSON decodes correctly`() {
        val sampleJson = """
            {
                "tag_name": "v4.0.3",
                "name": "ArtWallpaper v4.0.3",
                "body": "Fixed taskbar clearance and added auto-updater",
                "html_url": "https://github.com/Princeyyyy/art-wallpaper/releases/tag/v4.0.3",
                "prerelease": false,
                "assets": [
                    {
                        "name": "ArtWallpaper-4.0.3-Windows.msi",
                        "size": 45000000,
                        "browser_download_url": "https://github.com/Princeyyyy/art-wallpaper/releases/download/v4.0.3/ArtWallpaper-4.0.3-Windows.msi"
                    },
                    {
                        "name": "ArtWallpaper-4.0.3-macOS.dmg",
                        "size": 48000000,
                        "browser_download_url": "https://github.com/Princeyyyy/art-wallpaper/releases/download/v4.0.3/ArtWallpaper-4.0.3-macOS.dmg"
                    }
                ]
            }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true }
        val release = json.decodeFromString<GithubRelease>(sampleJson)

        assertEquals("v4.0.3", release.tag_name)
        assertEquals(2, release.assets.size)
        assertEquals("ArtWallpaper-4.0.3-Windows.msi", release.assets[0].name)
        assertEquals("ArtWallpaper-4.0.3-macOS.dmg", release.assets[1].name)
    }

    @Test
    fun `initial update manager status is Idle`() {
        val manager = UpdateManager(currentVersion = "4.0.2")
        assertEquals(UpdateStatus.Idle, manager.status.value)
    }
}
