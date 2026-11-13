import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import service.GithubAsset
import service.GithubRelease
import service.UpdateManager
import service.UpdateStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateManagerTest {

    @Test
    fun `isNewerVersion accurately compares semver strings`() {
        assertTrue(UpdateManager.isNewerVersion("4.0.3", "4.0.2"))
        assertTrue(UpdateManager.isNewerVersion("4.1.0", "4.0.2"))
        assertTrue(UpdateManager.isNewerVersion("5.0.0", "4.0.2"))
        assertTrue(UpdateManager.isNewerVersion("v4.0.3", "4.0.2"))
        assertTrue(UpdateManager.isNewerVersion("V4.1.0", "v4.0.2"))
        assertTrue(UpdateManager.isNewerVersion("4.1.1", "4.0.3"))
        assertTrue(UpdateManager.isNewerVersion("4.1.1", "4.1.0"))
        assertTrue(UpdateManager.isNewerVersion("v4.1.1", "v4.0.3"))
        assertTrue(UpdateManager.isNewerVersion("4.1.2", "4.0.3"))
        assertTrue(UpdateManager.isNewerVersion("4.1.2", "4.1.0"))
        assertTrue(UpdateManager.isNewerVersion("4.1.2", "4.1.1"))
        assertTrue(UpdateManager.isNewerVersion("v4.1.2", "4.1.0"))
        assertTrue(UpdateManager.isNewerVersion("4.1.3", "4.0.3"))
        assertTrue(UpdateManager.isNewerVersion("4.1.3", "4.1.0"))
        assertTrue(UpdateManager.isNewerVersion("4.1.3", "4.1.1"))
        assertTrue(UpdateManager.isNewerVersion("4.1.3", "4.1.2"))
        assertTrue(UpdateManager.isNewerVersion("v4.1.3", "4.1.0"))
        assertTrue(UpdateManager.isNewerVersion("4.1.4", "4.1.3"))
        assertTrue(UpdateManager.isNewerVersion("4.1.4", "4.1.0"))
        assertTrue(UpdateManager.isNewerVersion("v4.1.4", "4.0.3"))
        assertTrue(UpdateManager.isNewerVersion("4.1.5", "4.1.4"))
        assertTrue(UpdateManager.isNewerVersion("4.1.5", "4.1.0"))
        assertTrue(UpdateManager.isNewerVersion("v4.1.5", "4.0.3"))
        assertTrue(UpdateManager.isNewerVersion("4.1.6", "4.1.5"))
        assertTrue(UpdateManager.isNewerVersion("4.1.6", "4.1.0"))
        assertTrue(UpdateManager.isNewerVersion("v4.1.6", "4.0.3"))
        assertTrue(UpdateManager.isNewerVersion("4.2.3", "4.2.2"))
        assertTrue(UpdateManager.isNewerVersion("4.2.2", "4.2.1"))
        assertTrue(UpdateManager.isNewerVersion("4.2.0", "4.1.7"))
        assertTrue(UpdateManager.isNewerVersion("4.1.7", "4.1.6"))
        assertTrue(UpdateManager.isNewerVersion("4.1.7", "4.1.0"))
        assertTrue(UpdateManager.isNewerVersion("v4.1.7", "4.0.3"))

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

    @Test
    fun `consumeNotificationForVersion notifies once per distinct version`() {
        val manager = UpdateManager(currentVersion = "4.0.2")
        assertFalse(manager.consumeNotificationForVersion(""))
        assertTrue(manager.consumeNotificationForVersion("4.1.0"))
        assertFalse(manager.consumeNotificationForVersion("4.1.0"))
        assertTrue(manager.consumeNotificationForVersion("4.2.0"))
        assertFalse(manager.consumeNotificationForVersion("4.2.0"))
    }

    @Test
    fun `formatAssetSizeMb formats installer sizes accurately`() {
        assertNull(UpdateManager.formatAssetSizeMb(null))
        assertNull(UpdateManager.formatAssetSizeMb(GithubAsset("empty.msi", 0L, "https://example.com")))
        val formatted = UpdateManager.formatAssetSizeMb(
            GithubAsset("ArtWallpaper-4.2.0-Windows.msi", 45_000_000L, "https://example.com")
        )
        assertEquals("42.9 MB", formatted)
    }

    @Test
    fun `startBackgroundPolling notifies once per new version and stops cleanly`() = runBlocking {
        var checkCount = 0
        var activeVersion = "4.2.0"
        val notifiedVersions = mutableListOf<String>()
        val twoChecksObserved = CompletableDeferred<Unit>()

        val fakeManager = object : UpdateManager(currentVersion = "4.1.0") {
            override suspend fun checkForUpdates(silent: Boolean): UpdateStatus {
                checkCount += 1
                if (checkCount >= 2 && !twoChecksObserved.isCompleted) {
                    twoChecksObserved.complete(Unit)
                }
                return UpdateStatus.Available(
                    latestVersion = activeVersion,
                    releaseNotes = "Release notes for $activeVersion",
                    asset = null,
                    releaseUrl = "https://github.com/Princeyyyy/art-wallpaper/releases"
                )
            }
        }

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val job = fakeManager.startBackgroundPolling(
            scope = scope,
            intervalMillis = 1_000L,
            initialDelayMillis = 0L,
            isPollingEnabled = { true },
            onNewUpdate = { available -> notifiedVersions.add(available.latestVersion) }
        )

        twoChecksObserved.await()
        job.cancel()
        scope.cancel()

        assertTrue(checkCount >= 2)
        assertEquals(listOf("4.2.0"), notifiedVersions)
    }

    @Test
    fun `startBackgroundPolling respects disabled polling preference`() = runBlocking {
        var checkCount = 0
        val fakeManager = object : UpdateManager(currentVersion = "4.1.0") {
            override suspend fun checkForUpdates(silent: Boolean): UpdateStatus {
                checkCount += 1
                return UpdateStatus.UpToDate("4.1.0")
            }
        }

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val job = fakeManager.startBackgroundPolling(
            scope = scope,
            intervalMillis = 1_000L,
            initialDelayMillis = 10L,
            isPollingEnabled = { false },
            onNewUpdate = {}
        )
        delay(80L)
        job.cancel()
        scope.cancel()

        assertEquals(0, checkCount)
    }

    @Test
    fun `legacy settings JSON decodes with autoCheckUpdates enabled by default`() {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val legacy = json.decodeFromString<Settings>("""{"changeIntervalHours":24,"updateTimeHour":8}""")
        assertTrue(legacy.autoCheckUpdates)
        assertNull(legacy.dismissedUpdateVersion)
    }

    @Test
    fun `generateWindowsUpdateScript produces passive non-interactive msiexec commands`() {
        val msi = java.nio.file.Path.of("C:\\test\\ArtWallpaper-4.3.0.msi")
        val exe = java.nio.file.Path.of("C:\\Users\\User\\AppData\\Local\\Art Wallpaper\\Art Wallpaper.exe")
        val script = UpdateManager.generateWindowsUpdateScript(msi, exe)

        assertTrue(script.contains("msiexec.exe /i \"!MSI_PATH!\" /passive /norestart"))
        assertTrue(script.contains("tasklist /FI \"IMAGENAME eq Art Wallpaper.exe\""))
        assertTrue(script.contains("del \"!MSI_PATH!\""))
        // Canonical LOCALAPPDATA path is the primary relaunch target
        assertTrue(script.contains("%LOCALAPPDATA%\\Art Wallpaper\\Art Wallpaper.exe"))
        // start /wait ensures CMD blocks until msiexec completes
        assertTrue(script.contains("start \"ArtWallpaperInstaller\" /wait msiexec.exe"))
        // taskkill force-terminates any lingering instance before install
        assertTrue(script.contains("taskkill /F /IM \"Art Wallpaper.exe\""))
    }

    @Test
    fun `generateMacUpdateScript produces silent non-interactive hdiutil and ditto commands`() {
        val dmg = java.nio.file.Path.of("/tmp/ArtWallpaper-4.3.0.dmg")
        val app = java.nio.file.Path.of("/Applications/Art Wallpaper.app")
        val script = UpdateManager.generateMacUpdateScript(dmg, app)

        assertTrue(script.contains("hdiutil attach \"${'$'}DMG_PATH\""))
        assertTrue(script.contains("-nobrowse -readonly -quiet"))
        assertTrue(script.contains("ditto \"${'$'}SOURCE_APP\" \"${'$'}DEST_APP\""))
        assertTrue(script.contains("hdiutil detach"))
        assertTrue(script.contains("open "))
        // Extended 10-second wait and force-kill before replacing bundle
        assertTrue(script.contains("{1..10}"))
        assertTrue(script.contains("pkill -f \"Art Wallpaper\""))
        // Canonical /Applications path preferred for relaunch
        assertTrue(script.contains("/Applications/Art Wallpaper.app"))
    }

    @Test
    fun `resolveWindowsExecutable falls back correctly`() {
        val resolved = UpdateManager.resolveWindowsExecutable(
            command = null,
            localAppData = "C:\\CustomAppData\\Local"
        )
        assertEquals("C:/CustomAppData/Local/Art Wallpaper/Art Wallpaper.exe", resolved.toString().replace('\\', '/'))
    }

    @Test
    fun `resolveMacAppBundle resolves path or falls back`() {
        val resolved = UpdateManager.resolveMacAppBundle(
            command = "/Applications/Art Wallpaper.app/Contents/MacOS/Art Wallpaper",
            homeDir = "/Users/testuser"
        )
        assertEquals("/Applications/Art Wallpaper.app", resolved.toString().replace('\\', '/'))
    }
}
