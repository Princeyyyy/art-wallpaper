package service

import AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream

@Serializable
data class GithubRelease(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val html_url: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GithubAsset> = emptyList()
)

@Serializable
data class GithubAsset(
    val name: String,
    val size: Long = 0,
    val browser_download_url: String
)

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class UpToDate(val currentVersion: String) : UpdateStatus()
    data class Available(
        val latestVersion: String,
        val releaseNotes: String,
        val asset: GithubAsset?,
        val releaseUrl: String
    ) : UpdateStatus()
    data class Downloading(val progress: Float) : UpdateStatus()
    data class ReadyToInstall(val installerPath: Path) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

class UpdateManager(
    private val currentVersion: String = AppInfo.version,
    private val repoOwner: String = "Princeyyyy",
    private val repoName: String = "art-wallpaper"
) {
    private val logger = LoggerFactory.getLogger(UpdateManager::class.java)
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    private val updatesDir = Path.of(System.getProperty("user.home"), ".artwallpaper", "updates")

    /**
     * Checks GitHub Releases API for a newer version than currentVersion.
     */
    suspend fun checkForUpdates(silent: Boolean = false): UpdateStatus = withContext(Dispatchers.IO) {
        if (!silent) _status.value = UpdateStatus.Checking

        try {
            val url = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "ArtWallpaper-DesktopApp")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 404) {
                val state = UpdateStatus.UpToDate(currentVersion)
                _status.value = state
                return@withContext state
            }

            if (response.statusCode() != 200) {
                val state = UpdateStatus.Error("Server returned code ${response.statusCode()}")
                if (!silent) _status.value = state
                return@withContext state
            }

            val release = json.decodeFromString<GithubRelease>(response.body())
            val latestVersion = release.tag_name.trim().removePrefix("v").removePrefix("V")

            if (isNewerVersion(latestVersion, currentVersion)) {
                val os = System.getProperty("os.name", "").lowercase()
                val targetAsset = release.assets.firstOrNull { asset ->
                    if (os.contains("mac")) asset.name.endsWith(".dmg", ignoreCase = true)
                    else asset.name.endsWith(".msi", ignoreCase = true)
                } ?: release.assets.firstOrNull()

                val state = UpdateStatus.Available(
                    latestVersion = latestVersion,
                    releaseNotes = release.body ?: "New release available",
                    asset = targetAsset,
                    releaseUrl = release.html_url ?: "https://github.com/$repoOwner/$repoName/releases"
                )
                _status.value = state
                logger.info("New update available: v$latestVersion (current: v$currentVersion)")
                return@withContext state
            } else {
                val state = UpdateStatus.UpToDate(currentVersion)
                _status.value = state
                logger.info("Application is up to date (v$currentVersion)")
                return@withContext state
            }
        } catch (e: Exception) {
            logger.warn("Update check failed: ${e.message}")
            val state = UpdateStatus.Error(e.message ?: "Could not check for updates")
            if (!silent) _status.value = state
            return@withContext state
        }
    }

    /**
     * Downloads the release installer binary and launches it immediately.
     */
    suspend fun downloadAndInstall(asset: GithubAsset) = withContext(Dispatchers.IO) {
        try {
            _status.value = UpdateStatus.Downloading(0f)
            updatesDir.createDirectories()
            val destination = updatesDir.resolve(asset.name)

            val request = HttpRequest.newBuilder()
                .uri(URI.create(asset.browser_download_url))
                .header("User-Agent", "ArtWallpaper-DesktopApp")
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())

            if (response.statusCode() != 200) {
                _status.value = UpdateStatus.Error("Download failed with HTTP ${response.statusCode()}")
                return@withContext
            }

            val contentLength = response.headers().firstValueAsLong("content-length").orElse(asset.size)
            var totalBytesRead = 0L

            response.body().use { input: InputStream ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (contentLength > 0) {
                            val progress = (totalBytesRead.toFloat() / contentLength).coerceIn(0f, 1f)
                            _status.value = UpdateStatus.Downloading(progress)
                        }
                    }
                }
            }

            _status.value = UpdateStatus.ReadyToInstall(destination)
            logger.info("Installer downloaded successfully to: $destination")

            // Launch the installer
            install(destination)

        } catch (e: Exception) {
            logger.error("Download and install error", e)
            _status.value = UpdateStatus.Error("Failed to download update: ${e.message}")
        }
    }

    /**
     * Launches the native installer and terminates the application cleanly.
     */
    fun install(installerPath: Path) {
        val os = System.getProperty("os.name", "").lowercase()
        try {
            if (os.contains("windows")) {
                logger.info("Launching Windows MSI installer: $installerPath")
                // Start msiexec detached via cmd start so the installer runs independently and app terminates
                ProcessBuilder("cmd.exe", "/c", "start \"\" msiexec.exe /i \"${installerPath.toAbsolutePath()}\"").start()
                Thread.sleep(800)
                System.exit(0)
            } else if (os.contains("mac")) {
                logger.info("Mounting macOS DMG installer: $installerPath")
                ProcessBuilder("open", installerPath.toAbsolutePath().toString()).start()
                Thread.sleep(1000)
                System.exit(0)
            }
        } catch (e: Exception) {
            logger.error("Failed to launch installer", e)
            _status.value = UpdateStatus.Error("Could not launch installer: ${e.message}")
        }
    }

    companion object {
        fun isNewerVersion(latest: String, current: String): Boolean {
            val latestParts = latest.trim().removePrefix("v").removePrefix("V")
                .split('.').mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
            val currentParts = current.trim().removePrefix("v").removePrefix("V")
                .split('.').mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }

            val maxLen = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            return false
        }
    }
}
