import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AssetIntegrityTest {

    private fun assertResourceExists(resourcePath: String, minBytes: Int = 10) {
        val stream = javaClass.getResourceAsStream(resourcePath)
        assertNotNull(stream, "Resource missing from classpath: $resourcePath")
        val bytes = stream.use { it.readBytes() }
        assertTrue(
            bytes.size >= minBytes,
            "Resource $resourcePath is too small (${bytes.size} bytes, expected at least $minBytes)"
        )
    }

    @Test
    fun `root fallback assets exist and are valid`() {
        assertResourceExists("/app_icon.ico", minBytes = 1000)
        assertResourceExists("/tray_icon.png", minBytes = 500)
        assertResourceExists("/logback.xml", minBytes = 100)
    }

    @Test
    fun `branding assets exist and are non-empty`() {
        val brandingAssets = listOf(
            "/branding/logo.svg",
            "/branding/readme-banner-1600x420.png",
            "/branding/social-preview-1280x640.png",
            "/branding/preview-sheet.png"
        )
        brandingAssets.forEach { assertResourceExists(it, minBytes = 1000) }
    }

    @Test
    fun `core application icons exist across all standard resolutions`() {
        val coreIcons = listOf(
            "/icons/icon.ico",
            "/icons/icon.png",
            "/icons/icon-1024.png",
            "/icons/icon.icns",
            "/icons/16x16.png",
            "/icons/24x24.png",
            "/icons/32x32.png",
            "/icons/44x44.png",
            "/icons/48x48.png",
            "/icons/50x50.png",
            "/icons/64x64.png",
            "/icons/71x71.png",
            "/icons/89x89.png",
            "/icons/107x107.png",
            "/icons/128x128.png",
            "/icons/128x128@2x.png",
            "/icons/142x142.png",
            "/icons/150x150.png",
            "/icons/256x256.png",
            "/icons/284x284.png",
            "/icons/310x310.png",
            "/icons/512x512.png"
        )
        coreIcons.forEach { assertResourceExists(it, minBytes = 200) }
    }

    @Test
    fun `Windows and Store tile assets exist`() {
        val tileAssets = listOf(
            "/icons/tiles/Square30x30Logo.png",
            "/icons/tiles/Square44x44Logo.png",
            "/icons/tiles/Square71x71Logo.png",
            "/icons/tiles/Square89x89Logo.png",
            "/icons/tiles/Square107x107Logo.png",
            "/icons/tiles/Square142x142Logo.png",
            "/icons/tiles/Square150x150Logo.png",
            "/icons/tiles/Square284x284Logo.png",
            "/icons/tiles/Square310x310Logo.png",
            "/icons/tiles/StoreLogo.png",
            "/icons/tiles/Wide310x150Logo.png"
        )
        tileAssets.forEach { assertResourceExists(it, minBytes = 500) }
    }

    @Test
    fun `installer bitmaps exist and are valid`() {
        val installerBitmaps = listOf(
            "/installer/wix-banner-493x58.bmp",
            "/installer/wix-dialog-493x312.bmp",
            "/installer/wizard-large-164x314.bmp",
            "/installer/wizard-small-55x58.bmp"
        )
        installerBitmaps.forEach { assertResourceExists(it, minBytes = 1000) }
    }

    @Test
    fun `system tray color and monochrome icons exist`() {
        val trayAssets = listOf(
            "/tray/tray.ico",
            "/tray/tray-16.png",
            "/tray/tray-20.png",
            "/tray/tray-24.png",
            "/tray/tray-32.png",
            "/tray/tray-48.png",
            "/tray/tray-64.png",
            "/tray/tray-mono-black-16.png",
            "/tray/tray-mono-black-24.png",
            "/tray/tray-mono-black-32.png",
            "/tray/tray-mono-black.ico",
            "/tray/tray-mono-white-16.png",
            "/tray/tray-mono-white-24.png",
            "/tray/tray-mono-white-32.png",
            "/tray/tray-mono-white.ico"
        )
        trayAssets.forEach { assertResourceExists(it, minBytes = 100) }
    }
}
