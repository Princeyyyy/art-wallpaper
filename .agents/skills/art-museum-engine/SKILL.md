---
name: art-museum-engine
description: Deep architectural guide and technical specification for Art Wallpaper's museum picture frame engine, ambient bokeh wallpaper generation, and operating system desktop synchronization. Use when modifying ImageProcessor, WallpaperManager, WallpaperService, display scaling, or artwork rendering logic.
---

# Art Wallpaper Museum Picture Frame & Wallpaper Engine

This document provides a comprehensive architectural and mathematical reference for Art Wallpaper's image rendering pipeline, physical picture frame simulation, and operating system wallpaper synchronization.

---

## 1. End-to-End Pipeline Architecture

```
                      ┌────────────────────────────────────┐
                      │          MetMuseumSource           │
                      │    (The Met Museum REST API)       │
                      │  - hasImages=true filtering        │
                      │  - Department rotation caching     │
                      └─────────────────┬──────────────────┘
                                        │ Raw Image Stream (.jpg)
                                        ▼
                      ┌────────────────────────────────────┐
                      │          ArtworkProvider           │
                      │  - Temp cache downloading          │
                      │  - Deduplication via HistoryManager│
                      │  - Offline fallback failover       │
                      └─────────────────┬──────────────────┘
                                        │ High-res BufferedImage
                                        ▼
                      ┌────────────────────────────────────┐
                      │           ImageProcessor           │
                      │  - Physical display dimensioning   │
                      │  - Layer A: Ambient Bokeh Blur     │
                      │  - Layer B: Walnut Picture Frame   │
                      └─────────────────┬──────────────────┘
                                        │ Composed 24-bit JPEG
                                        ▼
                      ┌────────────────────────────────────┐
                      │       ArtworkStorageManager        │
                      │  - Save ~/.artwallpaper/artworks/  │
                      │  - Write metadata/ JSON            │
                      │  - Chronological mtime sorting     │
                      │  - 100-item retention & pruning    │
                      └────────┬───────────────────┬───────┘
                               │                   │
               ┌───────────────┘                   └───────────────┐
               ▼                                                   ▼
┌─────────────────────────────┐                     ┌─────────────────────────────┐
│      WallpaperService       │                     │         Compose UI          │
│ - Windows: Win32 JNA API    │                     │ - MainScreen Exhibition Card│
│ - macOS: AppleScript Events │                     │ - Dynamic ArtworkBitmapCache│
│ - Live OS Desktop Sync      │                     │ - Modification-time tracking│
└─────────────────────────────┘                     └─────────────────────────────┘
```

---

## 2. Dual-Layer Museum Wallpaper Engine (`ImageProcessor.kt`)

Every wallpaper rendered by `ImageProcessor.processImage` produces an ultra-high-definition canvas matching physical screen dimensions (e.g., 1920x1080, 2560x1440, 2880x1800, 3840x2160). The output is composed of two independent, non-interfering layers:

### Layer A: Untouched Ambient Background

The background provides an immersive atmosphere while ensuring desktop icons remain legible:

1. **Downsampling for Fast Blur**:
   - The artwork is downscaled by a factor of 10 into a small thumbnail.
   - Performing blur operations on this thumbnail drastically reduces CPU cycles and memory bandwidth while producing an ultra-smooth bokeh gradient.
2. **Separable Multi-Pass Box Blur (`fastBoxBlur`)**:
   - Executes separable horizontal and vertical sliding-window box blur passes.
   - Eliminates Gaussian blur transcendental math overhead while achieving an optically equivalent silk diffusion.
3. **Upscaling & Bilinear Interpolation**:
   - The blurred thumbnail is scaled up to the screen dimensions using bilinear interpolation (`RenderingHints.VALUE_INTERPOLATION_BILINEAR`).
4. **Dimming Wash**:
   - An even 20% alpha black wash (`Color(0, 0, 0, 55)`) is applied across the entire canvas.
5. **Radial Vignette**:
   - A `RadialGradientPaint` is applied with a focus point at the screen center:
     - Center color: `Color(0, 0, 0, 0)` (fully transparent).
     - Edge color: `Color(0, 0, 0, 110)` (soft cinematic vignette).
6. **Design Invariance Mandate**:
   - **Do not modify or alter Layer A's background parameters.** The silky bokeh, alpha wash, and radial vignette define Art Wallpaper's signature visual identity.

---

### Layer B: Authentic Walnut Picture Frame (`renderFramedArtwork`)

The foreground artwork is rendered as a physical museum exhibit centered within the display canvas, with clearance for system taskbars and docks.

#### Physical Geometry Calculations
```kotlin
val minDim = minOf(canvasWidth, canvasHeight)
val frameMoulding = (minDim * 0.045f).coerceIn(24f, 64f)
val filletWidth = (frameMoulding * 0.16f).coerceIn(4f, 10f)
val shadowBlur = (minDim * 0.035f).coerceIn(16f, 48f)
val sightEdgeBevel = 2f
```

#### Multi-Stage Rendering Pipeline

1. **Ambient Gallery Drop Shadow**:
   - Simulates 45-degree overhead museum track lighting.
   - Renders 8 progressive rounded rectangles casting downward with vertical offset:
     ```kotlin
     val offsetY = shadowBlur * 0.65f
     for (i in 8 downTo 1) {
         val spread = shadowBlur * (i / 8f)
         val alpha = (32 / i).coerceIn(2, 38)
         g2d.color = Color(0, 0, 0, alpha)
         g2d.fillRoundRect(
             (frameX - spread).toInt(),
             (frameY - spread + offsetY).toInt(),
             (frameWidth + spread * 2).toInt(),
             (frameHeight + spread * 2).toInt(),
             cornerRadius,
             cornerRadius
         )
     }
     ```
2. **Walnut Hardwood Moulding**:
   - Beveled corner mitering with 3-stop linear hardwood gradient:
     - Highlight: `#4A2A1A` (warm roasted walnut)
     - Midtone: `#2C1810` (deep classic walnut)
     - Deep Shadow: `#1A0E08` (rich aged mahogany)
   - Outer perimeter highlight line (0.8px, `#6B3E26`) for edge definition.
   - Fluted cove relief groove (`coveInset = frameMoulding * 0.38f`) with inner shadow (`#150A05`) and highlight (`#54301D`).
3. **Gilded Brass / Gold Leaf Fillet Inlay**:
   - Separates the walnut moulding from the painting rabbet.
   - 3-stop linear metallic brass gradient:
     - Light: `#E6C575` (polished gilded highlight)
     - Midtone: `#B88A3B` (classical museum brass)
     - Dark: `#7A5218` (antique bronze shadow)
4. **Archival Sight-Edge Bevel**:
   - A deep shadow groove (`#0E0805`, 1.5px width) where the fillet meets the canvas surface, simulating physical wood rabbet depth.
5. **Inner Canvas Bevel Shadow**:
   - A soft gradient shadow cast by the frame moulding inward onto the surface of the painting (`shadowDepth = minDim * 0.003f`), anchoring the artwork inside its frame.

---

## 3. Operating System Wallpaper Synchronization (`WallpaperService.kt`)

To ensure the desktop wallpaper and application state never drift apart, the engine maintains bidirectional synchronization with the host operating system.

### Windows Architecture (Win32 JNA)
- **Applying Wallpaper**:
  1. Sets Windows registry keys in `HKCU\Control Panel\Desktop`:
     - `WallpaperStyle = "10"` (Fill / Fit with ambient background).
     - `TileWallpaper = "0"`.
  2. Executes Win32 `SystemParametersInfoW`:
     ```kotlin
     User32.INSTANCE.SystemParametersInfoW(
         WinUser.SPI_SETDESKWALLPAPER,
         0,
         wallpaperPath.toAbsolutePath().toString(),
         WinUser.SPIF_UPDATEINIFILE or WinUser.SPIF_SENDCHANGE
     )
     ```
- **Reading Active Desktop Wallpaper**:
  Queries the registry via `Advapi32Util`:
  ```kotlin
  val pathStr = Advapi32Util.registryGetStringValue(
      WinReg.HKEY_CURRENT_USER,
      "Control Panel\\Desktop",
      "Wallpaper"
  )
  ```

### macOS Architecture (AppleScript)
- **Applying Wallpaper**:
  Executes AppleScript via `ProcessBuilder`:
  ```applescript
  tell application "System Events"
      tell every desktop
          set picture to POSIX file "/path/to/artwork.jpg"
      end tell
  end tell
  ```
- **Reading Active Desktop Wallpaper**:
  ```applescript
  tell application "System Events" to get picture of current desktop
  ```

### Live Synchronization Flow (`syncWithActiveDesktopWallpaper`)
`WallpaperManager.syncWithActiveDesktopWallpaper()` is called:
1. When the application launches.
2. When the main window wakes or unminimizes from the system tray.
3. When the user clicks the tray icon.

The method inspects the OS desktop wallpaper file, extracts its ID, loads its metadata from `ArtworkStorageManager.getArtworkMetadata(id)`, and immediately updates `_currentArtwork.value`.

---

## 4. In-Memory Cache Invalidation & Storage Management

### Dynamic Cache Invalidation (`ArtworkBitmapCache`)
- UI exhibition cards use Compose Desktop's `ArtworkBitmapCache`.
- The cache key is `Pair<Path, Long>`, where `Long` is the file's last modification timestamp (`Files.getLastModifiedTime(path)`).
- If an artwork is reprocessed or replaced on disk, the timestamp mismatch automatically invalidates stale in-memory bitmaps, immediately presenting the updated artwork without application restarts.

### Chronological Storage Ordering (`ArtworkStorageManager`)
- `getStoredArtworks()` sorts stored files descending by `Files.getLastModifiedTime()`.
- `getLatestStoredArtwork()` returns the most recently generated wallpaper file.
- Retention: Automatically caps the gallery at 100 artworks, pruning the oldest files while safeguarding the currently active wallpaper and cleaning orphaned image files lacking corresponding metadata JSON.
