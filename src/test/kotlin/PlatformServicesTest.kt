import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

class PlatformServicesTest {

    private lateinit var tempDir: Path

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("platform-services-test")
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 1: OS Detection & Factory Dispatch
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `wallpaper service factory creates appropriate instance`() {
        val originalOs = System.getProperty("os.name")
        try {
            System.setProperty("os.name", "Windows 11")
            val winService = WallpaperService.create()
            assertTrue(winService is WindowsWallpaperService)

            System.setProperty("os.name", "Mac OS X")
            val macService = WallpaperService.create()
            assertTrue(macService is MacWallpaperService)
        } finally {
            if (originalOs != null) System.setProperty("os.name", originalOs)
        }
    }

    @Test
    fun `autostart service factory creates appropriate instance`() {
        val originalOs = System.getProperty("os.name")
        try {
            System.setProperty("os.name", "Windows 10")
            val winAutoStart = AutoStartService.create()
            assertTrue(winAutoStart is WindowsAutoStartService)

            System.setProperty("os.name", "Mac OS X")
            val macAutoStart = AutoStartService.create()
            assertTrue(macAutoStart is MacAutoStartService)
        } finally {
            if (originalOs != null) System.setProperty("os.name", originalOs)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 2: macOS LaunchAgent Plist Generator
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `macOS plist generator produces valid launchd XML`() {
        val macService = MacAutoStartService(
            launchAgentsDir = tempDir,
            bundleId = "com.test.artwallpaper",
            appExecutablePath = "/Applications/Art Wallpaper.app/Contents/MacOS/Art Wallpaper"
        )

        val plist = macService.generatePlist()

        assertTrue(plist.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(plist.contains("<key>Label</key>"))
        assertTrue(plist.contains("<string>com.test.artwallpaper</string>"))
        assertTrue(plist.contains("<key>ProgramArguments</key>"))
        assertTrue(plist.contains("<string>--minimized</string>"))
        assertTrue(plist.contains("<string>--autostart</string>"))
        assertTrue(plist.contains("<key>RunAtLoad</key>"))
        assertTrue(plist.contains("<true/>"))
    }

    @Test
    fun `macOS autostart service lifecycle creates and removes plist file`() {
        val macService = MacAutoStartService(
            launchAgentsDir = tempDir,
            bundleId = "com.test.lifecycle"
        )

        assertFalse(macService.isEnabled())

        macService.enable()
        assertTrue(macService.isEnabled())
        assertTrue(tempDir.resolve("com.test.lifecycle.plist").exists())

        macService.disable()
        assertFalse(macService.isEnabled())
        assertFalse(tempDir.resolve("com.test.lifecycle.plist").exists())
    }
}
