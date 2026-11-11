import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

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
            "/tray/tray-36.png",
            "/tray/tray-40.png",
            "/tray/tray-48.png",
            "/tray/tray-64.png",
            "/tray/tray-96.png",
            "/tray/tray-128.png",
            "/tray/tray-mono-black-16.png",
            "/tray/tray-mono-black-18.png",
            "/tray/tray-mono-black-20.png",
            "/tray/tray-mono-black-22.png",
            "/tray/tray-mono-black-24.png",
            "/tray/tray-mono-black-32.png",
            "/tray/tray-mono-black-36.png",
            "/tray/tray-mono-black-40.png",
            "/tray/tray-mono-black-44.png",
            "/tray/tray-mono-black-48.png",
            "/tray/tray-mono-black-64.png",
            "/tray/tray-mono-black.ico",
            "/tray/tray-mono-white-16.png",
            "/tray/tray-mono-white-18.png",
            "/tray/tray-mono-white-20.png",
            "/tray/tray-mono-white-22.png",
            "/tray/tray-mono-white-24.png",
            "/tray/tray-mono-white-32.png",
            "/tray/tray-mono-white-36.png",
            "/tray/tray-mono-white-40.png",
            "/tray/tray-mono-white-44.png",
            "/tray/tray-mono-white-48.png",
            "/tray/tray-mono-white-64.png",
            "/tray/tray-mono-white.ico"
        )
        trayAssets.forEach { assertResourceExists(it, minBytes = 100) }
    }

    @Test
    fun `macOS bundle icon contains full iconset entries including small 16 32 and 64 sizes`() {
        val bytes = javaClass.getResourceAsStream("/icons/icon.icns")?.readBytes()
            ?: fail("Missing /icons/icon.icns")
        assertTrue(bytes.size > 10000)
        assertEquals("icns", String(bytes, 0, 4, Charsets.US_ASCII))

        val types = mutableSetOf<String>()
        var pos = 8
        while (pos + 8 <= bytes.size) {
            val type = String(bytes, pos, 4, Charsets.US_ASCII)
            val len = ((bytes[pos + 4].toInt() and 0xFF) shl 24) or
                ((bytes[pos + 5].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 6].toInt() and 0xFF) shl 8) or
                (bytes[pos + 7].toInt() and 0xFF)
            assertTrue(len >= 8, "Invalid chunk length for $type")
            types.add(type)
            pos += len
        }
        assertEquals(bytes.size, pos, "ICNS total byte size must match parsed chunks")
        listOf("TOC ", "icp4", "icp5", "icp6", "ic07", "ic08", "ic09", "ic10", "ic11", "ic12", "ic13", "ic14")
            .forEach { assertTrue(types.contains(it), "Missing ICNS entry $it") }
    }

    @Test
    fun `Windows app and tray ICO files contain sharp PNG payloads across standard DPI sizes`() {
        listOf("/icons/icon.ico", "/app_icon.ico", "/tray/tray.ico").forEach { resourcePath ->
            val bytes = javaClass.getResourceAsStream(resourcePath)?.readBytes()
                ?: fail("Missing $resourcePath")
            assertTrue(bytes.size > 64, "ICO too small: $resourcePath")

            fun u16(offset: Int): Int =
                (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
            fun u32(offset: Int): Int =
                u16(offset) or (u16(offset + 2) shl 16)

            assertEquals(0, u16(0), "Invalid ICO reserved field in $resourcePath")
            assertEquals(1, u16(2), "Expected ICO type=1 in $resourcePath")
            val count = u16(4)
            assertTrue(count >= 6, "Expected at least 6 resolution images in $resourcePath, found $count")

            val widths = mutableSetOf<Int>()
            for (idx in 0 until count) {
                val base = 6 + idx * 16
                val wRaw = bytes[base].toInt() and 0xFF
                val width = if (wRaw == 0) 256 else wRaw
                val payloadSize = u32(base + 8)
                val payloadOffset = u32(base + 12)
                assertTrue(payloadSize > 8 && payloadOffset + payloadSize <= bytes.size, "Invalid ICO payload bounds in $resourcePath")
                // Verify PNG signature 89 50 4E 47
                assertEquals(0x89, bytes[payloadOffset].toInt() and 0xFF, "Expected PNG header in $resourcePath size $width")
                assertEquals('P'.code, bytes[payloadOffset + 1].toInt() and 0xFF)
                assertEquals('N'.code, bytes[payloadOffset + 2].toInt() and 0xFF)
                assertEquals('G'.code, bytes[payloadOffset + 3].toInt() and 0xFF)
                widths.add(width)
            }
            listOf(16, 20, 24, 32, 48, 64).forEach { expected ->
                assertTrue(widths.contains(expected), "Missing ${expected}px entry in $resourcePath (found: $widths)")
            }
        }
    }
}
