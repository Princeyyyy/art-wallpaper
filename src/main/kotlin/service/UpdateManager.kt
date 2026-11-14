package service

import AppInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText

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

@Serializable
data class UpdateJournal(
    val fromVersion: String,
    val targetVersion: String,
    val startedAt: Long,
    val installerPath: String
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
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L
    ) : UpdateStatus()
    data class ReadyToInstall(val installerPath: Path) : UpdateStatus()
    object Installing : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

open class UpdateManager(
    private val currentVersion: String = AppInfo.version,
    private val repoOwner: String = "Princeyyyy",
    private val repoName: String = "art-wallpaper",
    var onBeforeExit: (() -> Unit)? = null,
    private val updatesDir: Path = Path.of(System.getProperty("user.home"), ".artwallpaper", "updates")
) {
    private val logger = LoggerFactory.getLogger(UpdateManager::class.java)
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    private val lastNotifiedVersion = AtomicReference<String?>(null)

    init {
        inspectPostUpdateResult()
    }

    fun dismissError() {
        if (_status.value is UpdateStatus.Error) {
            _status.value = UpdateStatus.Idle
        }
    }

    /**
     * Checks if a previous update run wrote a failure result file, surfacing clear feedback
     * to the user on relaunch if the OS installer failed or was cancelled.
     */
    private fun inspectPostUpdateResult() {
        try {
            val resultFile = updatesDir.resolve("update_result.txt")
            val journalFile = updatesDir.resolve("update_journal.json")
            if (resultFile.exists()) {
                val resultText = resultFile.readText().trim()
                val journal = if (journalFile.exists()) {
                    runCatching { json.decodeFromString<UpdateJournal>(journalFile.readText()) }.getOrNull()
                } else null

                resultFile.deleteIfExists()
                journalFile.deleteIfExists()

                if (resultText.startsWith("ERROR:", ignoreCase = true)) {
                    val code = resultText.substringAfter("ERROR:").trim()
                    val targetVer = journal?.targetVersion?.let { "v$it" } ?: "new version"
                    if (journal == null || isNewerVersion(journal.targetVersion, currentVersion)) {
                        val msg = "Update to $targetVer did not complete (installer exit code $code). Click Check / Retry to update again."
                        logger.warn(msg)
                        _status.value = UpdateStatus.Error(msg)
                    }
                } else if (resultText.startsWith("SUCCESS", ignoreCase = true)) {
                    logger.info("Post-update verification succeeded: running v$currentVersion")
                }
            }
        } catch (e: Exception) {
            logger.debug("Could not inspect post-update result: ${e.message}")
        }
    }

    /**
     * Checks GitHub Releases API for a newer version than currentVersion.
     */
    open suspend fun checkForUpdates(silent: Boolean = false): UpdateStatus = withContext(Dispatchers.IO) {
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
                if (!silent || _status.value !is UpdateStatus.Available) {
                    _status.value = state
                }
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Update check failed: ${e.message}")
            val state = UpdateStatus.Error(e.message ?: "Could not check for updates")
            if (!silent) _status.value = state
            return@withContext state
        }
    }

    /**
     * Downloads the release installer binary to a verified staging file and launches it immediately.
     */
    suspend fun downloadAndInstall(asset: GithubAsset, latestVersion: String? = null) = withContext(Dispatchers.IO) {
        val targetVersion = latestVersion?.takeIf { it.isNotBlank() }
            ?: (_status.value as? UpdateStatus.Available)?.latestVersion
            ?: ""
        try {
            _status.value = UpdateStatus.Downloading(0f, 0L, asset.size)
            updatesDir.createDirectories()
            val destination = updatesDir.resolve(asset.name)
            val partFile = updatesDir.resolve("${asset.name}.part")
            partFile.deleteIfExists()

            val request = HttpRequest.newBuilder()
                .uri(URI.create(asset.browser_download_url))
                .header("User-Agent", "ArtWallpaper-DesktopApp")
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())

            if (response.statusCode() != 200) {
                _status.value = UpdateStatus.Error("Download failed with HTTP ${response.statusCode()}")
                return@withContext
            }

            val contentLength = response.headers().firstValueAsLong("content-length").orElse(asset.size)
            var totalBytesRead = 0L
            var lastReportedPct = -1
            var lastReportTimeMs = 0L

            response.body().use { input: InputStream ->
                partFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        val nowMs = System.currentTimeMillis()
                        if (contentLength > 0) {
                            val progress = (totalBytesRead.toFloat() / contentLength).coerceIn(0f, 1f)
                            val pct = (progress * 100).toInt()
                            if (pct > lastReportedPct || (nowMs - lastReportTimeMs) >= 120L) {
                                lastReportedPct = pct
                                lastReportTimeMs = nowMs
                                _status.value = UpdateStatus.Downloading(progress, totalBytesRead, contentLength)
                            }
                        } else if ((nowMs - lastReportTimeMs) >= 200L) {
                            lastReportTimeMs = nowMs
                            _status.value = UpdateStatus.Downloading(0.5f, totalBytesRead, 0L)
                        }
                    }
                    output.flush()
                }
            }

            if (totalBytesRead <= 0L) {
                partFile.deleteIfExists()
                _status.value = UpdateStatus.Error("Downloaded installer file is empty. Please retry.")
                return@withContext
            }

            if (contentLength > 0L && totalBytesRead != contentLength) {
                partFile.deleteIfExists()
                _status.value = UpdateStatus.Error(
                    "Download interrupted (${formatBytesMb(totalBytesRead)} of ${formatBytesMb(contentLength)}). Please retry."
                )
                return@withContext
            }

            if (!verifyInstallerHeader(partFile, asset.name)) {
                partFile.deleteIfExists()
                _status.value = UpdateStatus.Error("Downloaded installer failed integrity check. Please retry.")
                return@withContext
            }

            Files.move(partFile, destination, StandardCopyOption.REPLACE_EXISTING)
            _status.value = UpdateStatus.Downloading(1f, totalBytesRead, contentLength)

            // Write update journal so relaunched instance can verify completion
            if (targetVersion.isNotBlank()) {
                try {
                    val journal = UpdateJournal(
                        fromVersion = currentVersion,
                        targetVersion = targetVersion,
                        startedAt = System.currentTimeMillis(),
                        installerPath = destination.toAbsolutePath().toString()
                    )
                    updatesDir.resolve("update_journal.json").writeText(json.encodeToString(journal))
                } catch (_: Exception) {}
            }

            _status.value = UpdateStatus.ReadyToInstall(destination)
            logger.info("Installer downloaded and verified (${formatBytesMb(totalBytesRead)}): $destination")

            // Launch the installer
            install(destination)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Download and install error", e)
            _status.value = UpdateStatus.Error("Failed to download update: ${e.message}")
        }
    }

    private fun verifyInstallerHeader(file: Path, assetName: String): Boolean {
        return try {
            val size = Files.size(file)
            if (size < 4L) return false
            if (assetName.endsWith(".msi", ignoreCase = true)) {
                val header = ByteArray(4)
                Files.newInputStream(file).use { it.read(header) }
                // Windows Installer MSI OLE2 Compound Document magic bytes: D0 CF 11 E0
                val isOle2 = (header[0] == 0xD0.toByte() &&
                    header[1] == 0xCF.toByte() &&
                    header[2] == 0x11.toByte() &&
                    header[3] == 0xE0.toByte())
                if (!isOle2) {
                    logger.warn("MSI header verification failed for $file")
                }
                isOle2
            } else {
                true
            }
        } catch (e: Exception) {
            logger.warn("Installer verification error: ${e.message}")
            false
        }
    }

    /**
     * Marks [version] as notified and returns true only if it has not been notified yet in this session.
     */
    fun consumeNotificationForVersion(version: String): Boolean {
        if (version.isBlank()) return false
        while (true) {
            val prev = lastNotifiedVersion.get()
            if (prev == version) return false
            if (lastNotifiedVersion.compareAndSet(prev, version)) return true
        }
    }

    /**
     * Periodically polls GitHub releases in the background and fires [onNewUpdate] once per newly discovered version.
     */
    fun startBackgroundPolling(
        scope: CoroutineScope,
        intervalMillis: Long = Duration.ofHours(6).toMillis(),
        initialDelayMillis: Long = 8_000L,
        isPollingEnabled: () -> Boolean = { true },
        onNewUpdate: (UpdateStatus.Available) -> Unit
    ): Job {
        return scope.launch {
            if (initialDelayMillis > 0L) {
                delay(initialDelayMillis)
            }
            while (isActive) {
                try {
                    if (isPollingEnabled()) {
                        val result = checkForUpdates(silent = true)
                        if (result is UpdateStatus.Available && consumeNotificationForVersion(result.latestVersion)) {
                            onNewUpdate(result)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("Background release check failed: ${e.message}")
                }
                delay(intervalMillis.coerceAtLeast(1_000L))
            }
        }
    }

    /**
     * Seamlessly applies the update in the background without setup wizards and restarts the application.
     * Transitions to [UpdateStatus.Installing] first so the UI can display a message before the process exits.
     */
    fun install(installerPath: Path) {
        val os = System.getProperty("os.name", "").lowercase()
        try {
            _status.value = UpdateStatus.Installing
            // Give the UI render loop time to display the Installing state before the process exits.
            Thread.sleep(700)
            if (os.contains("windows")) {
                launchWindowsSilentUpdate(installerPath)
            } else if (os.contains("mac")) {
                launchMacSilentUpdate(installerPath)
            } else {
                logger.warn("Automatic silent install not supported on $os; launching default handler")
                java.awt.Desktop.getDesktop().open(installerPath.toFile())
                Thread.sleep(800)
                runCatching { onBeforeExit?.invoke() }
                System.exit(0)
            }
        } catch (e: Exception) {
            logger.error("Failed to launch silent installer", e)
            _status.value = UpdateStatus.Error("Could not launch installer: ${e.message}")
        }
    }

    /**
     * Prepares and starts a detached helper script on Windows that waits for Art Wallpaper to exit,
     * invokes msiexec in passive mode (clean progress bar, zero wizard screens/prompts), and relaunches
     * the updated application binary.
     */
    fun launchWindowsSilentUpdate(
        installerPath: Path,
        targetExe: Path = resolveWindowsExecutable(),
        exitProcess: () -> Unit = { System.exit(0) }
    ) {
        logger.info("Preparing silent Windows update for: $installerPath -> target: $targetExe")
        val scriptContent = generateWindowsUpdateScript(installerPath, targetExe)
        updatesDir.createDirectories()
        val scriptFile = updatesDir.resolve("apply_update.bat")
        Files.writeString(scriptFile, scriptContent)

        // Release single-instance lock before spawning updater so the relaunched app is never blocked
        runCatching { onBeforeExit?.invoke() }

        // Pass paths via environment variables AND baked into script, avoiding cmd.exe /c nested quote bugs
        val pb = ProcessBuilder(
            "cmd.exe", "/c", "start", "ArtWallpaperUpdate", "/min", "cmd.exe", "/c",
            scriptFile.toAbsolutePath().toString()
        )
        pb.environment()["ART_UPDATE_MSI"] = installerPath.toAbsolutePath().toString()
        pb.environment()["ART_UPDATE_EXE"] = targetExe.toAbsolutePath().toString()
        pb.directory(updatesDir.toFile())
        pb.start()
        logger.info("Launched silent Windows update script. Terminating application to apply update.")
        try {
            Thread.sleep(500)
        } catch (_: InterruptedException) {}
        exitProcess()
    }

    /**
     * Prepares and starts a detached helper script on macOS that waits for Art Wallpaper to exit,
     * mounts the DMG silently without opening Finder (-nobrowse -readonly), copies the updated .app bundle
     * using ditto (preserving code signatures and permissions), unmounts the DMG, and relaunches the app.
     */
    fun launchMacSilentUpdate(
        installerPath: Path,
        targetApp: Path = resolveMacAppBundle(),
        exitProcess: () -> Unit = { System.exit(0) }
    ) {
        logger.info("Preparing silent macOS update for: $installerPath -> target: $targetApp")
        val scriptContent = generateMacUpdateScript(installerPath, targetApp)
        updatesDir.createDirectories()
        val scriptFile = updatesDir.resolve("apply_update.sh")
        Files.writeString(scriptFile, scriptContent)
        scriptFile.toFile().setExecutable(true, false)

        // Release single-instance lock before spawning updater
        runCatching { onBeforeExit?.invoke() }

        val pb = ProcessBuilder(
            "/bin/bash", scriptFile.toAbsolutePath().toString(),
            installerPath.toAbsolutePath().toString(),
            targetApp.toAbsolutePath().toString()
        )
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        pb.redirectError(ProcessBuilder.Redirect.DISCARD)
        pb.directory(updatesDir.toFile())
        pb.start()
        logger.info("Launched silent macOS update script. Terminating application to apply update.")
        try {
            Thread.sleep(500)
        } catch (_: InterruptedException) {}
        exitProcess()
    }

    companion object {
        fun resolveWindowsExecutable(command: String? = null, localAppData: String? = null): Path {
            val cmd = command ?: runCatching { ProcessHandle.current().info().command().orElse(null) }.getOrNull()
            if (!cmd.isNullOrBlank()) {
                val path = Path.of(cmd)
                if (Files.isRegularFile(path) && path.fileName.toString().equals("Art Wallpaper.exe", ignoreCase = true)) {
                    return path
                }
            }

            val appData = localAppData ?: System.getenv("LOCALAPPDATA")
            if (!appData.isNullOrBlank()) {
                val primary = Path.of(appData, "Art Wallpaper", "Art Wallpaper.exe")
                if (Files.isRegularFile(primary)) return primary
                val programs = Path.of(appData, "Programs", "Art Wallpaper", "Art Wallpaper.exe")
                if (Files.isRegularFile(programs)) return programs
            }

            val fallbackBase = appData ?: System.getProperty("user.home", ".")
            return Path.of(fallbackBase, "Art Wallpaper", "Art Wallpaper.exe")
        }

        fun resolveMacAppBundle(command: String? = null, homeDir: String? = null): Path {
            val cmd = command ?: runCatching { ProcessHandle.current().info().command().orElse(null) }.getOrNull()
            if (!cmd.isNullOrBlank()) {
                var current: Path? = Path.of(cmd)
                while (current != null) {
                    if (current.fileName?.toString()?.endsWith(".app", ignoreCase = true) == true) {
                        return current
                    }
                    current = current.parent
                }
            }

            val defaultApps = Path.of("/Applications", "Art Wallpaper.app")
            if (Files.exists(defaultApps)) return defaultApps

            val home = homeDir ?: System.getProperty("user.home", ".")
            val userApps = Path.of(home, "Applications", "Art Wallpaper.app")
            if (Files.exists(userApps)) return userApps

            return defaultApps
        }

        fun generateWindowsUpdateScript(installerPath: Path, targetExe: Path): String {
            val safeMsiPath = installerPath.toAbsolutePath().toString().replace("/", "\\")
            val safeExePath = targetExe.toAbsolutePath().toString().replace("/", "\\")
            return """
                @echo off
                setlocal enabledelayedexpansion

                set "MSI_PATH=%~1"
                if "!MSI_PATH!"=="" set "MSI_PATH=!ART_UPDATE_MSI!"
                if "!MSI_PATH!"=="" set "MSI_PATH=$safeMsiPath"
                if not exist "!MSI_PATH!" (
                    for %%f in ("%~dp0*.msi") do set "MSI_PATH=%%~ff"
                )

                set "EXE_PATH=%~2"
                if "!EXE_PATH!"=="" set "EXE_PATH=!ART_UPDATE_EXE!"
                if "!EXE_PATH!"=="" set "EXE_PATH=$safeExePath"

                set "RESULT_FILE=%~dp0update_result.txt"
                set "MSI_LOG=%~dp0last_msi_install.log"

                rem [1/4] Wait up to 10 seconds for Art Wallpaper.exe to exit cleanly
                for /l %%i in (1,1,10) do (
                    tasklist /FI "IMAGENAME eq Art Wallpaper.exe" 2>nul | find /i "Art Wallpaper.exe" >nul
                    if !ERRORLEVEL! EQU 0 (
                        ping 127.0.0.1 -n 2 >nul
                    ) else (
                        goto :proc_done
                    )
                )
                rem Force terminate any lingering instance so files are unlocked for replacement
                taskkill /F /IM "Art Wallpaper.exe" >nul 2>&1
                ping 127.0.0.1 -n 2 >nul
                :proc_done

                rem Remove any stale single-instance lock files before installing and relaunching
                if exist "%USERPROFILE%\.artwallpaper\app.lock" del /f /q "%USERPROFILE%\.artwallpaper\app.lock" >nul 2>&1
                if exist "%USERPROFILE%\.artwallpaper\app.port" del /f /q "%USERPROFILE%\.artwallpaper\app.port" >nul 2>&1

                rem [2/4] Run Windows Installer in passive mode and WAIT for it to finish!
                start "ArtWallpaperInstaller" /wait msiexec.exe /i "!MSI_PATH!" /passive /norestart /L*v "!MSI_LOG!"
                set "MSI_EXIT=!ERRORLEVEL!"

                rem If error 1638 (another version already installed), retry with REINSTALLMODE=vomus
                if !MSI_EXIT! EQU 1638 (
                    start "ArtWallpaperInstaller" /wait msiexec.exe /i "!MSI_PATH!" REINSTALL=ALL REINSTALLMODE=vomus /passive /norestart /L*v "!MSI_LOG!"
                    set "MSI_EXIT=!ERRORLEVEL!"
                )

                rem Exit code 0 is Success, 3010 is Success with reboot recommended
                if !MSI_EXIT! EQU 0 (
                    echo SUCCESS>"!RESULT_FILE!"
                    del "!MSI_PATH!" >nul 2>&1
                ) else if !MSI_EXIT! EQU 3010 (
                    echo SUCCESS>"!RESULT_FILE!"
                    del "!MSI_PATH!" >nul 2>&1
                ) else (
                    echo ERROR:!MSI_EXIT!>"!RESULT_FILE!"
                )

                rem Brief settle pause so Windows releases file handles on the newly written executable
                ping 127.0.0.1 -n 2 >nul

                rem [3/4] Resolve installed executable path
                set "LAUNCH_TARGET="
                if exist "%LOCALAPPDATA%\Art Wallpaper\Art Wallpaper.exe" (
                    set "LAUNCH_TARGET=%LOCALAPPDATA%\Art Wallpaper\Art Wallpaper.exe"
                ) else if not "!EXE_PATH!"=="" (
                    if exist "!EXE_PATH!" (
                        set "LAUNCH_TARGET=!EXE_PATH!"
                    )
                )
                if "!LAUNCH_TARGET!"=="" (
                    if exist "%LOCALAPPDATA%\Programs\Art Wallpaper\Art Wallpaper.exe" (
                        set "LAUNCH_TARGET=%LOCALAPPDATA%\Programs\Art Wallpaper\Art Wallpaper.exe"
                    ) else if exist "%ProgramFiles%\Art Wallpaper\Art Wallpaper.exe" (
                        set "LAUNCH_TARGET=%ProgramFiles%\Art Wallpaper\Art Wallpaper.exe"
                    )
                )

                rem [4/4] Relaunch Art Wallpaper and verify process starts
                if exist "%LOCALAPPDATA%\Art Wallpaper\Art Wallpaper.exe" (
                    start "" "%LOCALAPPDATA%\Art Wallpaper\Art Wallpaper.exe"
                ) else if not "!LAUNCH_TARGET!"=="" (
                    start "" "!LAUNCH_TARGET!"
                )

                ping 127.0.0.1 -n 3 >nul
                tasklist /FI "IMAGENAME eq Art Wallpaper.exe" 2>nul | find /i "Art Wallpaper.exe" >nul
                if !ERRORLEVEL! NEQ 0 (
                    if not "!LAUNCH_TARGET!"=="" (
                        start "" "!LAUNCH_TARGET!"
                    )
                )

                rem Self-delete helper batch script
                (goto) 2>nul & del "%~f0"
            """.trimIndent()
        }

        fun generateMacUpdateScript(installerPath: Path, targetApp: Path): String {
            return """
                #!/bin/bash
                set -e

                DMG_PATH="${'$'}1"
                TARGET_APP="${'$'}2"
                RESULT_FILE="${'$'}(dirname "${'$'}DMG_PATH")/update_result.txt"

                # Wait up to 10 seconds for Art Wallpaper to exit cleanly
                for i in {1..10}; do
                    if pgrep -f "Art Wallpaper" >/dev/null 2>&1; then
                        sleep 1
                    else
                        break
                    fi
                done

                # Force-kill any remaining instance so app bundle files are unlocked for replacement
                pkill -f "Art Wallpaper" 2>/dev/null || true
                sleep 1

                # Remove stale single-instance lock files
                rm -f "${'$'}HOME/.artwallpaper/app.lock" "${'$'}HOME/.artwallpaper/app.port" 2>/dev/null || true

                # Create temporary mount point
                MOUNT_DIR=${'$'}(mktemp -d -t art_mount_XXXXXX)

                # Attach DMG quietly with no Finder window
                hdiutil attach "${'$'}DMG_PATH" -mountpoint "${'$'}MOUNT_DIR" -nobrowse -readonly -quiet

                # Locate .app inside the mounted volume
                SOURCE_APP=${'$'}(find "${'$'}MOUNT_DIR" -maxdepth 2 -name "*.app" -print -quit)

                if [ -n "${'$'}SOURCE_APP" ] && [ -d "${'$'}SOURCE_APP" ]; then
                    TARGET_DIR="${'$'}(dirname "${'$'}TARGET_APP")"
                    APP_NAME="${'$'}(basename "${'$'}SOURCE_APP")"
                    DEST_APP="${'$'}TARGET_DIR/${'$'}APP_NAME"

                    # Copy bundle using ditto to preserve macOS code signatures and permissions
                    if [ -w "${'$'}DEST_APP" ] || [ -w "${'$'}TARGET_DIR" ]; then
                        rm -rf "${'$'}DEST_APP"
                        ditto "${'$'}SOURCE_APP" "${'$'}DEST_APP"
                    else
                        osascript -e "do shell script \"rm -rf \\\"${'$'}DEST_APP\\\" && ditto \\\"${'$'}SOURCE_APP\\\" \\\"${'$'}DEST_APP\\\"\" with administrator privileges"
                    fi

                    # Clear quarantine attributes so Gatekeeper never blocks relaunch
                    xattr -cr "${'$'}DEST_APP" 2>/dev/null || true

                    # Detach DMG
                    hdiutil detach "${'$'}MOUNT_DIR" -quiet || hdiutil detach "${'$'}MOUNT_DIR" -force -quiet
                    rmdir "${'$'}MOUNT_DIR" 2>/dev/null || true

                    echo "SUCCESS" > "${'$'}RESULT_FILE"

                    # Relaunch the newly updated app — prefer canonical /Applications path, fall back to DEST_APP
                    if [ -d "/Applications/Art Wallpaper.app" ]; then
                        open "/Applications/Art Wallpaper.app"
                    else
                        open "${'$'}DEST_APP"
                    fi

                    sleep 2
                    if ! pgrep -f "Art Wallpaper" >/dev/null 2>&1; then
                        open -a "${'$'}DEST_APP" 2>/dev/null || true
                    fi
                else
                    echo "ERROR:NO_APP_IN_DMG" > "${'$'}RESULT_FILE"
                    hdiutil detach "${'$'}MOUNT_DIR" -quiet || hdiutil detach "${'$'}MOUNT_DIR" -force -quiet
                    rmdir "${'$'}MOUNT_DIR" 2>/dev/null || true
                    if [ -d "/Applications/Art Wallpaper.app" ]; then
                        open "/Applications/Art Wallpaper.app"
                    elif [ -d "${'$'}TARGET_APP" ]; then
                        open "${'$'}TARGET_APP"
                    fi
                fi

                # Clean up DMG and script
                rm -f "${'$'}DMG_PATH"
                rm -f "${'$'}0"
            """.trimIndent()
        }

        fun formatBytesMb(bytes: Long): String {
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            return String.format(Locale.US, "%.1f MB", mb)
        }

        fun formatAssetSizeMb(asset: GithubAsset?): String? {
            if (asset == null || asset.size <= 0L) return null
            return formatBytesMb(asset.size)
        }

        /**
         * Deletes obsolete installer files (.msi / .dmg / .part) and temporary update scripts from ~/.artwallpaper/updates
         * to prevent disk bloat.
         */
        fun cleanupOldInstallers(updatesDir: Path = Path.of(System.getProperty("user.home"), ".artwallpaper", "updates")) {
            val logger = LoggerFactory.getLogger(UpdateManager::class.java)
            try {
                if (!Files.exists(updatesDir)) return
                Files.list(updatesDir).use { stream ->
                    stream.forEach { file ->
                        val name = file.fileName.toString()
                        if (name.endsWith(".msi", ignoreCase = true) ||
                            name.endsWith(".dmg", ignoreCase = true) ||
                            name.endsWith(".part", ignoreCase = true) ||
                            name.startsWith("apply_update", ignoreCase = true)
                        ) {
                            try {
                                val sizeBytes = Files.size(file)
                                val sizeMb = sizeBytes / (1024.0 * 1024.0)
                                Files.deleteIfExists(file)
                                logger.info("Cleaned up obsolete update file: $name (freed ${"%.1f".format(sizeMb)} MB)")
                            } catch (e: Exception) {
                                logger.warn("Could not delete obsolete update file $name: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to clean up updates directory: ${e.message}")
            }
        }

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
