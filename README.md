<p align="center">
  <img src="src/main/resources/branding/readme-banner-1600x420.png" alt="ArtWallpaper Banner" width="100%" />
</p>

<p align="center">
  <img src="src/main/resources/icons/128x128.png" alt="ArtWallpaper Logo" width="64" height="64" />
</p>

<h1 align="center">ArtWallpaper Manager</h1>

<p align="center">
  <strong>Transform your Windows desktop into a daily museum gallery.</strong><br>
  Curated masterpieces from The Metropolitan Museum of Art delivered automatically to your screen every morning.
</p>

<p align="center">
  <a href="#-features"><img src="https://img.shields.io/badge/Platform-Windows%2010%20%7C%2011-0078D6?logo=windows&logoColor=white" alt="Platform" /></a>
  <a href="#-features"><img src="https://img.shields.io/badge/UI-Compose%20Desktop-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose Desktop" /></a>
  <a href="#-features"><img src="https://img.shields.io/badge/Language-Kotlin%201.9-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" /></a>
  <a href="https://github.com/Princeyyyy/art-wallpaper/releases"><img src="https://img.shields.io/badge/Release-v3.3.0-C8A97E" alt="Release" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-CC%20BY--NC%204.0-lightgrey" alt="License" /></a>
</p>

---

## 🎨 Asset Showcase

<p align="center">
  <img src="src/main/resources/branding/preview-sheet.png" alt="ArtWallpaper Asset Pack" width="90%" />
</p>

ArtWallpaper includes a complete professional asset pack:
- **Application Icons** (`src/main/resources/icons/`): Multi-resolution Windows executable icons (`icon.ico` from 16x16 to 256x256), master 1024x1024 artwork, and macOS bundle assets (`icon.icns`).
- **Windows Start & Store Tiles** (`src/main/resources/icons/tiles/`): Full suite of Windows Start Menu and Microsoft Store tiles (`Square30x30Logo` up to `Square310x310Logo`, `StoreLogo`, and `Wide310x150Logo`).
- **System Tray Graphics** (`src/main/resources/tray/`): Pixel-perfect multi-DPI color icons (`tray-16` to `tray-64`, `tray.ico`) and native high-contrast monochrome variants (`tray-mono-white` and `tray-mono-black`) for Windows dark and light taskbars.
- **Installer Artwork** (`src/main/resources/installer/`): WiX MSI banners (`wix-banner-493x58.bmp`), dialog backgrounds (`wix-dialog-493x312.bmp`), and wizard sidebars (`wizard-large-164x314.bmp`).
- **Branding** (`src/main/resources/branding/`): Vector master logo (`logo.svg`), high-res README banner (`1600x420`), and GitHub social preview card (`1280x640`).

---

## ✨ Features

- 🏛️ **Museum-Grade Curation**: Draws from the public collection of The Metropolitan Museum of Art (400,000+ open-access artworks).
- 🌅 **Automatic Daily Rotation**: Configurable refresh time (default 07:00 AM) that persists across reboots.
- 🖼️ **Full-Bleed Gallery UI**: Clean editorial dark aesthetic with subtle scrims, title/artist typography, and no visual clutter.
- ⚡ **Native System Tray Menu**: Runs discreetly in the Windows taskbar with pause/resume, instant refresh, and notification support.
- 💾 **Smart Offline Fallback**: High-resolution artworks and JSON metadata stay saved locally in your personal gallery; if the internet is down, ArtWallpaper rotates through saved gems.
- 🧹 **Automatic Storage Pruning**: Keeps up to 100 recent artworks, automatically cleaning up orphaned files and cache artifacts.
- 🚀 **Windows Auto-Start**: Registers directly into the Windows Registry (`HKCU\Software\Microsoft\Windows\CurrentVersion\Run`) for seamless boot integration.
- 🛡️ **Crash-Resilient Settings**: Dual-layer settings storage with automatic `.json.bak` backup restoration.

---

## 🚀 Getting Started

### Installation via MSI (Recommended)
1. Download the latest `Art Wallpaper.msi` installer from [Releases](https://github.com/Princeyyyy/art-wallpaper/releases).
2. Run the installer. ArtWallpaper installs into:
   ```
   C:\Users\%USERNAME%\AppData\Local\Art Wallpaper
   ```
3. Launch ArtWallpaper. The first-run onboarding screen will guide you through setup and set your first masterpiece!

### Command-Line Arguments
ArtWallpaper supports several flags for automated environments and troubleshooting:
```powershell
# Launch minimized directly to system tray (used by Windows AutoStart)
"Art Wallpaper.exe" --minimized --autostart

# Reset and purge all local caches, history, and settings
"Art Wallpaper.exe" --clear-data
```

---

## 📁 Data Storage

All user data is stored safely in `%USERPROFILE%\.artwallpaper\`:

| Path | Purpose |
|---|---|
| `artworks/` | High-resolution wallpapers (`{id}.jpg`) for active use and offline rotation |
| `metadata/` | Artwork information (`{id}.json` with title, artist, year, medium) |
| `settings.json` | Active user preferences (update time, auto-start, notifications) |
| `settings.json.bak` | Redundant backup copy automatically restored if primary file is corrupted |
| `history.json` | Record of visited artwork IDs to prevent duplicate wallpapers |
| `logs/` | Daily rolling diagnostic logs |
| `app.lock` | Single-instance lock storing active process ID |

---

## 🎮 System Tray Controls

Right-click the ArtWallpaper tray icon in your Windows notification area:
- **Open Art Wallpaper**: Brings the main gallery window to the foreground.
- **Next Wallpaper**: Fetches and applies a new masterpiece immediately.
- **Pause Auto-Update**: Halts scheduled updates while keeping the current wallpaper.
- **Resume Auto-Update**: Resumes the 30-minute background check loop.
- **Exit**: Performs safe coroutine cleanup, releases locks, and closes the application.

---

## 🧪 Testing & CI/CD Pipeline

The project features comprehensive test coverage simulating real user flows:
- **Unit Tests**: `HistoryManagerTest`, `SettingsTest`, `RetryPolicyTest`, `ArtworkStorageManagerTest`, `WallpaperManagerTest`, `ServiceControllerTest`.
- **Integration Tests**: `UserFlowTest` (real file I/O tests covering onboarding, deduplication, storage, roundtrips, offline fallback, orphan pruning).
- **Asset Integrity Tests**: `AssetIntegrityTest` (validates all icons, tray graphics, installer bitmaps, and brand vectors).
- **GitHub Actions Workflows**:
  - `ci.yml`: Runs `.\gradlew compileKotlin` and `.\gradlew test` on every push and pull request.
  - `release.yml`: Manually triggered workflow (`workflow_dispatch`) that runs tests, bumps version numbers, builds the Windows MSI installer, and publishes a GitHub Release with installer binaries attached.

---

## 📜 License

Licensed under [Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)](http://creativecommons.org/licenses/by-nc/4.0/).
Artworks delivered by the application belong to the public domain collections of The Metropolitan Museum of Art.
