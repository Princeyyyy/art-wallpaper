# Art Wallpaper Developer Guide

This document provides a technical overview of the **Art Wallpaper** codebase, its subsystem architecture, and procedures for local debugging and development.

---

## Architecture and Component Map

The application follows a modular, layered architecture designed for cross-platform desktop performance and reliability:

```
src/main/kotlin/
├── Main.kt                     # Application entrypoint, single-instance lock, tray init, Compose window
├── AppInfo.kt                  # Version resolution from dynamic properties resource
├── data/
│   └── MetMuseumSource.kt      # API client for The Met (hasImages filtering, multi-department rotation)
├── platform/
│   ├── WallpaperService.kt     # Cross-platform wallpaper interface
│   │   ├── WindowsWallpaperService  # Win32 JNA SystemParametersInfo & registry integration
│   │   └── MacWallpaperService      # AppleScript / System Events & Finder integration
│   ├── WindowsPlatform.kt      # Win32 dark title bar, window rounded corners, DWM integration
│   └── MacPlatform.kt          # macOS notification center, dark theme, app activation
├── service/
│   ├── ArtworkProvider.kt      # Orchestrates fetching, caching, and failover across museum sources
│   ├── ArtworkStorageManager.kt# Manages saved artworks (~/.artwallpaper/artworks/), chronological sorting
│   ├── HistoryManager.kt       # Tracks shown artwork IDs to prevent repetition (~/.artwallpaper/history.json)
│   ├── ServiceController.kt    # Background service lifecycle, 30-minute rotation timer, manual curation
│   ├── SingleInstanceManager.kt# IPC port + app.lock file ensuring only one instance runs per user
│   ├── UpdateManager.kt        # GitHub releases polling, semver comparison, installer auto-cleanup
│   ├── WallpaperManager.kt     # State persistence, schedule evaluation, wallpaper change trigger
│   └── image/
│       └── ImageProcessor.kt   # Dual-layer wallpaper generator: ambient bokeh blur + walnut picture frame
└── ui/
    ├── MainScreen.kt           # Primary Compose Desktop UI, exhibition card, settings overlay
    ├── SystemTrayMenu.kt       # Native AWT system tray menu (Windows notification area / macOS menu bar)
    ├── components/
    │   └── AnimatedComponents.kt # Dynamic modification-time bitmap cache, pulsing buttons, curatorial chips
    └── theme/
        ├── Color.kt            # Palette definitions (museum dark & warm paper tones)
        └── Theme.kt            # Jetpack Compose MaterialTheme wrapper
```

---

## The Museum Wallpaper Pipeline

When an artwork is curated:
1. `MetMuseumSource` queries `https://collectionapi.metmuseum.org/public/collection/v1/search?hasImages=true&departmentId=X&q=*` to ensure candidate artworks have high-resolution images in the public domain.
2. The image stream is downloaded to a temporary cache file.
3. `ImageProcessor.processImage` executes:
   - **Target Resolution**: Queries physical screen pixels via `DisplayHelper.getScreenDimensions()` (e.g. 1920x1080, 2560x1440, 2880x1800).
   - **Ambient Bokeh Background**: Downsamples to thumbnail, executes multi-pass separable box blur (`fastBoxBlur`), applies a 20% alpha wash and radial vignette.
   - **Museum Picture Frame**: Renders 8-level ambient gallery drop shadow, beveled walnut wood moulding with cove relief, gilded brass fillet inlay, archival sight-edge bevel, and canvas surface shadow.
4. `ArtworkStorageManager` saves the processed image to `~/.artwallpaper/artworks/<id>.jpg` and writes `~/.artwallpaper/metadata/<id>.json`.
5. `WallpaperService.setWallpaper` applies the image to the desktop:
   - On Windows: Sets registry `WallpaperStyle = 10` (Fill) and triggers `SystemParametersInfoW(SPI_SETDESKWALLPAPER)`.
   - On macOS: Runs AppleScript targeting all active desktop spaces.
6. `WallpaperManager` persists `wallpaper_state.json`, records the artwork in `history.json`, and notifies `_currentArtwork.value`.
7. `ArtworkBitmapCache` detects the file modification timestamp and immediately re-renders the UI exhibition card.

---

## Testing Best Practices

### 1. Test Isolation
Unit tests must never touch the real user directory. Always inject `tempDir`:

```kotlin
@Test
fun `sample test with isolated storage`() {
    val tempDir = Files.createTempDirectory("test-isolated")
    try {
        val storage = ArtworkStorageManager(tempDir)
        // ... assertions ...
    } finally {
        tempDir.toFile().deleteRecursively()
    }
}
```

### 2. Coroutine Exception Handling
Never catch `Exception` without rethrowing `CancellationException` in coroutines:

```kotlin
try {
    suspendAction()
} catch (e: CancellationException) {
    throw e // Critical for coroutine structured concurrency
} catch (e: Exception) {
    logger.error("Operation failed", e)
}
```

---

## Building Native Packages Locally

### Windows MSI Installer (WiX Toolset)
```powershell
.\gradlew packageMsi
```
The output MSI installer is generated at:
`build/compose/binaries/main/msi/Art Wallpaper-<version>.msi`

### macOS DMG Disk Image
```bash
./gradlew packageDmg
```
The output DMG disk image is generated at:
`build/compose/binaries/main/dmg/Art Wallpaper-<version>.dmg`

---

## Debugging and Logs

Application logs are written using Logback:
- **General Log**: `~/.artwallpaper/logs/app.log` (1-day retention, 5MB rolling cap).
- **Dedicated Error Log**: `~/.artwallpaper/logs/error.log` (30-day retention, captures only `ERROR` events).
- **Update Directory**: `~/.artwallpaper/updates` (cleaned automatically on startup by `UpdateManager.cleanupOldInstallers()`).
