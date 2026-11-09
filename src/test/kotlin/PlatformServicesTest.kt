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
    // Test 0: AppInfo — dynamic versioning & bundle identity
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `AppInfo BUNDLE_ID is com prince artwallpaper`() {
        assertEquals("com.prince.artwallpaper", AppInfo.BUNDLE_ID)
    }

    @Test
    fun `AppInfo version is a valid semver string`() {
        val v = AppInfo.version
        assertTrue(v.isNotBlank(), "version must not be blank")
        // Must match X.Y.Z with optional pre-release suffix
        val semverRegex = Regex("""^\d+\.\d+\.\d+.*$""")
        assertTrue(semverRegex.matches(v), "version '$v' must match semver X.Y.Z pattern")
    }

    @Test
    fun `AppInfo APP_NAME is correct`() {
        assertEquals("Art Wallpaper", AppInfo.APP_NAME)
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

    // ─────────────────────────────────────────────────────────────────────────
    // Test 3: Platform-Native Integrations (Win 11 & macOS 27)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `SystemThemeDetector returns boolean safely across platforms`() {
        val dark = SystemThemeDetector.isDark()
        // Must return true or false without throwing any JNA or ProcessBuilder exception
        assertTrue(dark || !dark)
    }

    @Test
    fun `WindowsPlatform dark title bar and rounded corners methods execute without exception`() {
        // Even when called with a mock or non-display window, must never throw uncaught exceptions
        val frame = java.awt.Frame()
        try {
            WindowsPlatform.applyDarkTitleBar(frame, true)
            WindowsPlatform.applyDarkTitleBar(frame, false)
            WindowsPlatform.applyRoundedCorners(frame)
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun `WindowsPlatform registerAppUserModelId and bringToFront execute safely`() {
        WindowsPlatform.registerAppUserModelId("com.prince.artwallpaper")
        val frame = java.awt.Frame()
        try {
            WindowsPlatform.bringToFront(frame)
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun `MacPlatform hideFromDock, activateApp and showNotification execute safely without throwing`() {
        // Safe no-op on Windows/Linux; guarded against crashing on macOS
        MacPlatform.hideFromDock()
        MacPlatform.activateApp()
        MacPlatform.showNotification("Test Title", "Test Message with \"quotes\"")
    }

    @Test
    fun `SystemFontFamily initializes safely without error`() {
        assertNotNull(SystemFontFamily)
    }
}
