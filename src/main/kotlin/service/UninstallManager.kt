package service

import AutoStartService
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.win32.W32APIOptions
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists

object UninstallManager {
    private val logger = LoggerFactory.getLogger(UninstallManager::class.java)
    const val UPGRADE_CODE = "89ABC678-DEF0-1234-5678-ABCDEF123457"

    private interface Msi : Library {
        companion object {
            val INSTANCE: Msi? by lazy {
                try {
                    Native.load("msi", Msi::class.java, W32APIOptions.DEFAULT_OPTIONS)
                } catch (e: Throwable) {
                    null
                }
            }
        }

        fun MsiEnumRelatedProducts(
            lpUpgradeCode: String,
            dwReserved: Int,
            iProductIndex: Int,
            lpProductBuf: CharArray
        ): Int
    }

    /**
     * Executes the uninstallation flow:
     * 1. Disables auto-start
     * 2. Cleans up ~/.artwallpaper if deleteUserData is true
     * 3. Spawns the native uninstaller
     * 4. Exits the running application cleanly
     */
    fun performUninstall(deleteUserData: Boolean) {
        logger.info("Initiating uninstallation (deleteUserData=$deleteUserData)")

        // 1. Disable Auto-Start so the system doesn't try to restart it
        try {
            AutoStartService.create().disable()
        } catch (e: Exception) {
            logger.warn("Could not disable auto-start: ${e.message}")
        }

        // 2. Clean up user data if requested
        if (deleteUserData) {
            try {
                val appDataDir = Path.of(System.getProperty("user.home"), ".artwallpaper")
                if (appDataDir.exists()) {
                    appDataDir.toFile().deleteRecursively()
                    logger.info("Successfully cleaned user data directory: $appDataDir")
                }
            } catch (e: Exception) {
                logger.warn("Could not delete user data directory: ${e.message}")
            }
        }

        // 3. Launch native uninstaller
        val os = System.getProperty("os.name", "").lowercase()
        if (os.contains("windows")) {
            launchWindowsUninstaller()
        } else if (os.contains("mac")) {
            launchMacUninstaller()
        }

        // 4. Terminate process cleanly
        try {
            Thread.sleep(600)
        } catch (_: Exception) {}
        System.exit(0)
    }

    private fun launchWindowsUninstaller() {
        val productCode = findInstalledProductCode()
        if (productCode != null) {
            logger.info("Launching msiexec for ProductCode: $productCode")
            ProcessBuilder("msiexec.exe", "/x", productCode).start()
        } else {
            logger.warn("ProductCode not found via API, falling back to upgrade UUID or generic msiexec")
            try {
                ProcessBuilder("msiexec.exe", "/x", "{$UPGRADE_CODE}").start()
            } catch (e: Exception) {
                logger.error("Could not launch fallback msiexec", e)
            }
        }
    }

    private fun launchMacUninstaller() {
        logger.info("Opening Applications folder on macOS")
        try {
            val bundlePath = UpdateManager.resolveMacAppBundle().toAbsolutePath().toString()
            ProcessBuilder("open", "-R", bundlePath)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        } catch (e: Exception) {
            logger.error("Could not open Applications folder on macOS", e)
        }
    }

    /**
     * Resolves the installed Windows MSI ProductCode GUID for ArtWallpaper.
     */
    fun findInstalledProductCode(): String? {
        // Method 1: JNA MsiEnumRelatedProducts with UpgradeCode
        try {
            val buf = CharArray(39)
            val res = Msi.INSTANCE?.MsiEnumRelatedProducts("{$UPGRADE_CODE}", 0, 0, buf)
            if (res == 0) {
                val code = String(buf).trim { it <= ' ' || it == '\u0000' }
                if (code.isNotEmpty()) {
                    logger.info("Found ProductCode via MsiEnumRelatedProducts: $code")
                    return code
                }
            }
        } catch (e: Throwable) {
            logger.debug("MsiEnumRelatedProducts with braces failed: ${e.message}")
        }

        try {
            val buf = CharArray(39)
            val res = Msi.INSTANCE?.MsiEnumRelatedProducts(UPGRADE_CODE, 0, 0, buf)
            if (res == 0) {
                val code = String(buf).trim { it <= ' ' || it == '\u0000' }
                if (code.isNotEmpty()) {
                    logger.info("Found ProductCode via MsiEnumRelatedProducts: $code")
                    return code
                }
            }
        } catch (e: Throwable) {
            logger.debug("MsiEnumRelatedProducts without braces failed: ${e.message}")
        }

        // Method 2: Query Windows Registry Uninstall keys via JNA Advapi32Util
        try {
            val roots = listOf(WinReg.HKEY_CURRENT_USER, WinReg.HKEY_LOCAL_MACHINE)
            val paths = listOf(
                "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
                "Software\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall"
            )
            for (root in roots) {
                for (subPath in paths) {
                    if (!Advapi32Util.registryKeyExists(root, subPath)) continue
                    val subKeys = Advapi32Util.registryGetKeys(root, subPath)
                    for (key in subKeys) {
                        val keyPath = "$subPath\\$key"
                        try {
                            if (Advapi32Util.registryValueExists(root, keyPath, "DisplayName")) {
                                val name = Advapi32Util.registryGetStringValue(root, keyPath, "DisplayName")
                                if (name.equals("Art Wallpaper", ignoreCase = true)) {
                                    if (key.startsWith("{") && key.endsWith("}")) {
                                        logger.info("Found ProductCode via registry key: $key")
                                        return key
                                    }
                                    if (Advapi32Util.registryValueExists(root, keyPath, "UninstallString")) {
                                        val uString = Advapi32Util.registryGetStringValue(root, keyPath, "UninstallString")
                                        val match = Regex("""\{[0-9a-fA-F-]+\}""").find(uString)
                                        if (match != null) {
                                            logger.info("Found ProductCode via UninstallString: ${match.value}")
                                            return match.value
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Throwable) {
            logger.debug("Registry search for ProductCode failed: ${e.message}")
        }

        return null
    }

    /**
     * Calculates the estimated disk space used by ~/.artwallpaper in MB.
     */
    fun getEstimatedUserDataSizeMb(): Long {
        return try {
            val dir = Path.of(System.getProperty("user.home"), ".artwallpaper").toFile()
            if (dir.exists()) {
                val bytes = dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                bytes / (1024 * 1024)
            } else 0L
        } catch (_: Exception) {
            0L
        }
    }
}
