# ArtWallpaper End-to-End Flow Audit & Verification Guide

This document maps and audits every user-facing interaction, state transition, background process, and failure recovery path across ArtWallpaper.

---

## Architecture Summary & Entry Points

```
[MainKt: main()]
   ├── Single-Instance Guard (.artwallpaper/app.lock)
   ├── Settings (.artwallpaper/settings.json + .bak)
   ├── WindowsWallpaperService (JNA / SystemParametersInfoW)
   ├── HistoryManager (.artwallpaper/history.json)
   ├── ArtworkStorageManager (.artwallpaper/artworks/ + metadata/)
   ├── ArtworkProvider (MetMuseumSource + Cache + Offline fallback)
   ├── WallpaperManager (State coordinator + Periodic check logic + Mutex)
   ├── ServiceController (Lifecycle + 30m periodic loop + 24h cleanup loop)
   └── SystemTrayMenu (AWT TrayIcon + PopupMenu + Minimized window control)
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
     - **Step 3 (Windows Integration)**: Clear toggles and visual explanation for auto-start at boot and subtle taskbar tray behavior.
     - **Step 4 (Live Curation & Reveal)**: Live step-by-step curation status (Connecting -> Downloading -> Enhancing -> Applying), desktop wallpaper is set, and artwork title/artist are revealed with "ENTER GALLERY" confirmation button.
  4. State updates: `isFirstRun = false`, user's chosen update time and auto-start settings are saved atomically, and background service starts.
  5. Window transitions smoothly into the main gallery view.

### 2. Auto-Start on System Boot
* **Trigger:** Windows starts and launches `Art Wallpaper.exe --minimized --autostart`.
* **Expected Sequence:**
  1. `Main.kt` inspects command-line flags: `startMinimized = true`, `isAutoStart = true`.
  2. Main window starts invisible (`isWindowVisible = false`).
  3. System tray icon initializes with context menu in the Windows notification area.
  4. Background service starts:
     - Performs initial check via `WallpaperManager.checkAndUpdateIfNeeded()`.
     - If the daily update time has passed and today's update hasn't run, updates wallpaper automatically.
     - Starts 30-minute periodic check loop (`startPeriodicCheck()`).
     - Starts 24-hour cleanup loop (`startCleanupJob()`).

### 3. Manual Wallpaper Refresh ("Next Artwork")
* **Trigger:** User clicks **"NEXT ARTWORK"** in `MainScreen`.
* **Expected Sequence:**
  1. Button triggers `isLoading = true`, showing a centered spinner and loading overlay.
  2. Button disables to prevent concurrent clicks.
  3. Background coroutine calls `serviceController.nextWallpaper()`.
  4. Mutex in `WallpaperManager` prevents parallel execution.
  5. API fetches next unvisited artwork from Met Museum, skipping any IDs present in `HistoryManager`.
  6. New image is saved to disk; wallpaper is applied to Windows desktop.
  7. Flow collector in `MainScreen` detects the new artwork title and dismisses loading state.
  8. Artwork crossfades/renders in the main card; overlay updates title and artist credit.

### 4. Settings Configuration (Auto-Start & Time Selection)
* **Trigger:** User clicks the gear icon in the top right of `MainScreen`.
* **Expected Sequence:**
  1. `SettingsDialog` opens centered with sections: `SYSTEM`, `WALLPAPER`, `NOTIFICATIONS`.
  2. **Toggle Auto-Start:**
     - Calls `WindowsAutoStart.enable()` or `WindowsAutoStart.disable()`.
     - Directly modifies Windows Registry value `ArtWallpaper`.
     - Settings state updates and triggers debounce restart of background service.
  3. **Change Daily Update Time:**
     - User clicks the current time (e.g. `07:00`).
     - Custom dark spinner dialog allows selecting Hour (`00`–`23`) and Minute (`00`–`55`).
     - Clicking **"SET"** saves `updateTimeHour`, `updateTimeMinute`, and sets `hasSetUpdateTime = true`.
     - Settings persist atomically with backup copy (`settings.json.bak`).
  4. **Click "CLOSE":** Dialog dismisses smoothly; main screen reflects updated schedule.

### 5. System Tray Interactions
* **Trigger:** User interacts with tray icon in Windows taskbar.
* **Paths:**
  - **Left Click or Right Click → "Open Art Wallpaper":** Invokes `onShowWindow`, sets `isWindowVisible = true`, restores and brings window to foreground.
  - **"Next Wallpaper":** Triggers background refresh without requiring main window.
  - **"Pause Auto-Update":** Halts periodic checks; menu updates to "Resume Auto-Update".
  - **"Exit":** Calls `serviceController.cleanup()` (terminating jobs and releasing file locks), deletes `app.lock`, and terminates process safely.

### 6. Window Minimize to Tray
* **Trigger:** User clicks window close button (X).
* **Expected Sequence:**
  1. `onCloseRequest` intercepts window close.
  2. Sets `isWindowVisible = false` (window hides instead of exiting).
  3. System tray displays a native notification: `"Art Wallpaper is still running"`.
  4. Background service continues running normally.

### 7. Offline & Fallback Scenario
* **Trigger:** Device disconnects from Wi-Fi / Ethernet or Met API is unreachable.
* **Expected Sequence:**
  1. `ConnectivityChecker.isNetworkAvailable()` detects offline state (or request throws `NetworkUnavailableException`).
  2. `ArtworkProvider.fetchArtwork()` catches network unavailability and routes to `getRandomCachedArtwork()`.
  3. System inspects `~/.artwallpaper/artworks/*.jpg` for previously stored high-res images and their accompanying metadata.
  4. Fallback image is selected and applied without crashing.
  5. UI displays previously saved artwork or informative error banner if storage is empty.

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
| History Persistence | `HistoryManager` | `HistoryManagerTest` | Covered |
| Legacy Format Migration | `HistoryManager` | `HistoryManagerTest` | Covered |
| FIFO Trimming (100 items) | `HistoryManager` | `HistoryManagerTest`, `UserFlowTest` | Covered |
| Settings Serialization & Roundtrip | `Settings` | `SettingsTest`, `UserFlowTest` | Covered |
| Retry & Exponential Backoff | `RetryPolicy` | `RetryPolicyTest` | Covered |
| Artwork Disk Storage & Cleanup | `ArtworkStorageManager` | `ArtworkStorageManagerTest` | Covered |
| Orphan Image/Meta Pruning | `ArtworkStorageManager` | `ArtworkStorageManagerTest`, `UserFlowTest` | Covered |
| Mutex & Wallpaper Delegation | `WallpaperManager` | `WallpaperManagerTest` | Covered |
| Service Lifecycle & Startup | `ServiceController` | `ServiceControllerTest` | Covered |
| Onboarding & First-Run Flow | `MainKt` / `Settings` | `UserFlowTest` | Covered |
| Offline Fallback Retrieval | `ArtworkProvider` | `UserFlowTest` | Covered |
