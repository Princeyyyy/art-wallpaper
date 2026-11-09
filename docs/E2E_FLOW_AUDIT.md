# ArtWallpaper v4.0.0 End-to-End Flow Audit & Verification Guide

This document maps and audits every user-facing interaction, state transition, background process, and failure recovery path across ArtWallpaper on Windows and macOS.

---

## Architecture Summary & Entry Points

```
[MainKt: main()]
   ├── Single-Instance Guard (.artwallpaper/app.lock)
   ├── Settings (.artwallpaper/settings.json + .bak)
   ├── WallpaperService (Cross-Platform Interface)
   │     ├── WindowsWallpaperService (Win32 JNA / SystemParametersInfoW)
   │     └── MacWallpaperService (AppleScript / System Events)
   ├── AutoStartService (Cross-Platform Interface)
   │     ├── WindowsAutoStartService (HKCU Run Key Registry)
   │     └── MacAutoStartService (~/Library/LaunchAgents/ .plist)
   ├── Platform Integrations
   │     ├── WindowsPlatform (DWM Immersive Dark Titlebar + Round Corners via JNA)
   │     ├── MacPlatform (Accessory policy / Dock hiding + osascript Notification Center)
   │     └── SystemThemeDetector (Windows Registry / macOS defaults live theme sync)
   ├── HistoryManager (.artwallpaper/history.json)
   ├── ArtworkStorageManager (.artwallpaper/artworks/ + metadata/)
   ├── ArtworkProvider (MetMuseumSource + Cache + Offline fallback)
   ├── WallpaperManager (State coordinator + Periodic check logic + Mutex)
   ├── ServiceController (Lifecycle + 30m precise loop + 24h cleanup loop)
   └── SystemTrayMenu (AWT TrayIcon / NSStatusItem + Dynamic PopupMenu + Tooltips)
```

---

## User Journeys & End-to-End Flows

### 1. Fresh Installation & Guided 4-Step Onboarding
* **Trigger:** User launches application for the first time (`isFirstRun = true` in `Settings`).
* **Expected Sequence:**
  1. App initializes single-instance lock file (`~/.artwallpaper/app.lock`).
  2. `Settings.loadSavedSettings()` initializes default settings with `isFirstRun = true`.
  3. Window renders at 960x640 with the interactive `GuidedOnboarding` stepper (4-step animated flow):
     - **Step 1 (Welcome & Value)**: Brand emblem, 400,000+ works from The Met, offline gallery overview.
     - **Step 2 (Daily Schedule)**: Presets (07:00 AM, 09:00 AM, 18:00 PM) + interactive spinner for any custom time.
     - **Step 3 (Platform Integration)**: Clear toggles and visual explanation for auto-start at boot and subtle taskbar / menu bar behavior.
     - **Step 4 (Live Curation & Reveal)**: Live step-by-step curation status (Connecting -> Downloading -> Enhancing -> Applying), desktop wallpaper is set, and artwork title/artist are revealed with "ENTER GALLERY" confirmation button.
  4. State updates: `isFirstRun = false`, user's chosen update time and auto-start settings are saved atomically, and background service starts.
  5. Window transitions smoothly into the main gallery view.

### 2. Auto-Start on System Boot (Windows & macOS)
* **Trigger:** OS launches `Art Wallpaper --minimized --autostart` at user login.
* **Expected Sequence:**
  1. `Main.kt` inspects command-line flags: `startMinimized = true`, `isAutoStart = true`.
  2. Main window starts invisible (`isWindowVisible = false`).
  3. System tray / menu bar icon initializes in Windows notification area or macOS top menu bar.
  4. Background service starts:
     - Performs initial check via `WallpaperManager.checkAndUpdateIfNeeded()`.
     - If the daily update time has passed and today's update hasn't run, updates wallpaper automatically.
     - Starts 30-minute periodic check loop (`startPeriodicCheck()`).
     - Starts 24-hour cleanup loop (`startCleanupJob()`).

### 3. Manual Wallpaper Refresh ("Next Artwork")
* **Trigger:** User clicks **"NEXT ARTWORK"** in `MainScreen` or selects `"Next Wallpaper"` in the tray menu.
* **Expected Sequence:**
  1. Button triggers `isLoading = true`, showing a centered spinner and loading overlay.
  2. Button disables to prevent concurrent clicks.
  3. Background coroutine calls `serviceController.nextWallpaper()`.
  4. Mutex in `WallpaperManager` prevents parallel execution.
  5. API fetches next unvisited artwork from Met Museum, skipping any IDs present in `HistoryManager`.
  6. New image is saved to disk; wallpaper is applied to Windows or macOS desktop.
  7. Crossfade smoothly fades in the new artwork; title/artist overlay updates.

### 4. Settings Configuration (Auto-Start & Time Selection)
* **Trigger:** User clicks the gear icon in the top right of `MainScreen`.
* **Expected Sequence:**
  1. `SettingsDialog` opens with spring scale animation: `SYSTEM INTEGRATION`, `DAILY SCHEDULE`, `NOTIFICATIONS`, `ABOUT`.
  2. **Toggle Auto-Start:**
     - On Windows: updates `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`.
     - On macOS: creates or removes `~/Library/LaunchAgents/com.prince.artwallpaper.plist`.
     - Settings state updates and triggers debounce restart of background service.
  3. **Change Daily Update Time:**
     - User clicks the current time (e.g. `07:00`).
     - Dark spinner dialog allows selecting Hour (`00`–`23`) and Minute (`00`–`55`).
     - Clicking **"CONFIRM"** saves `updateTimeHour`, `updateTimeMinute`, and sets `hasSetUpdateTime = true`.
  4. **Click "DONE":** Dialog dismisses smoothly; main screen reflects updated schedule.

### 5. System Tray & Menu Bar Interactions
* **Trigger:** User interacts with tray icon in taskbar (Windows) or menu bar (macOS).
* **Paths:**
  - **Hover Tooltip**: Displays current artwork title and artist: `"Art Wallpaper: <Title> - <Artist>"`.
  - **"Current: <Title>"**: Top menu item displays the active artwork.
  - **"Open Art Wallpaper"**: Invokes `onShowWindow`, restores window to foreground.
  - **"Next Wallpaper"**: Triggers background refresh without requiring main window.
  - **Dynamic "Pause / Resume Auto-Update"**: Reflects active service status in real time.
  - **"Exit"**: Calls `serviceController.cleanup()`, deletes `app.lock`, and terminates process safely.

### 6. Window Minimize to Tray
* **Trigger:** User clicks window close button (X).
* **Expected Sequence:**
  1. `onCloseRequest` intercepts window close.
  2. Sets `isWindowVisible = false` (window hides instead of exiting).
  3. Displays a native notification: `"Art Wallpaper is still running"`.
  4. Background service continues running normally.

### 7. Offline & Fallback Scenario
* **Trigger:** Device disconnects from internet or Met API is unreachable.
* **Expected Sequence:**
  1. `ConnectivityChecker.isNetworkAvailable()` detects offline state.
  2. `ArtworkProvider.fetchArtwork()` catches network unavailability and routes to `getRandomCachedArtwork()`.
  3. System inspects `~/.artwallpaper/artworks/*.jpg` for previously stored high-res images and metadata.
  4. Fallback image is selected and applied without crashing.

### 8. History Cycle & Storage Pruning
* **Trigger:** Over time, artwork history accumulates beyond maximum limits.
* **Expected Sequence:**
  1. `HistoryManager` caps memory/disk entries at 100 items (FIFO trimming).
  2. When Met Museum source exhausts random search or cycles, history can be cleared or oldest pruned.
  3. `ArtworkStorageManager.cleanup()` ensures maximum 100 images are stored on disk, deleting oldest modified files and orphan JSON metadata.

---

## Automated Verification Matrix

| Area | Component | Covered by Test | Status |
|---|---|---|---|
| Single-Instance Lock Race & Stale PID Recovery | `MainKt` / Lock | `LifecycleAndStressTest` | Covered |
| Concurrency Stress (500 items, 10 workers) | `HistoryManager` | `LifecycleAndStressTest` | Covered |
| Rapid Lifecycle Transitions (No Deadlocks) | `ServiceController` | `LifecycleAndStressTest` | Covered |
| Mutex Throttling for Wallpaper Fetches | `WallpaperManager` | `LifecycleAndStressTest` | Covered |
| Malformed JSON History Self-Healing | `HistoryManager` | `CorruptDataResilienceTest` | Covered |
| Corrupt Metadata Gallery Resilience | `ArtworkStorageManager` | `CorruptDataResilienceTest` | Covered |
| Cross-Platform OS Factory Dispatch | `WallpaperService`, `AutoStartService` | `PlatformServicesTest` | Covered |
| macOS LaunchAgent Plist XML Generator | `MacAutoStartService` | `PlatformServicesTest` | Covered |
| Complete Asset Pack (60+ Icons, Tiles, Tray, Bitmaps) | `resources/` | `AssetIntegrityTest` | Covered |
| Guided 4-Step Onboarding Full Flow | `MainScreen` / `Settings` | `UserFlowTest` | Covered |
| Offline Fallback Rotation | `ArtworkProvider` | `UserFlowTest` | Covered |
| Settings Serialization & Roundtrip | `Settings` | `SettingsTest`, `UserFlowTest` | Covered |
| Retry & Exponential Backoff Bounds | `RetryPolicy` | `RetryPolicyTest` | Covered |
