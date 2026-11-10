# AI Agent Guidelines & Repository Directives

This document defines core behavioral constraints, architecture rules, and development guidelines for all AI agents (Antigravity, Claude, Cursor, Copilot) assisting with the **Art Wallpaper** codebase.

---

## 1. Git and Open-Source Governance Rules

1. **`master` is Protected**:
   - Never commit directly to `master` or push to `master`.
   - When asked to implement a feature or fix, work on a dedicated branch (`feat/<name>`, `fix/<name>`).
2. **Owner-Only Merges & Releases**:
   - Only the repository owner (`@Princeyyyy`) has the authority to approve PRs, merge into `master`, or trigger releases.
   - Automated tests run on PRs. Never attempt to bypass CI checks.
3. **Commit Cleanliness**:
   - Format commits according to Conventional Commits: `feat:`, `fix:`, `docs:`, `perf:`, `refactor:`, `test:`, `chore:`.

---

## 2. Architectural Guidelines and Best Practices

### Architecture
- **Language**: Kotlin 2.1.0 targeting JVM 17.
- **UI Framework**: Jetpack Compose Desktop 1.8.0-alpha03.
- **Single Source of Truth Versioning**: The application version is governed exclusively by `appVersion` in `gradle.properties`. Never hardcode version strings in source code; read `AppInfo.version` dynamically.

### Coroutines & Exception Handling
- **Structured Concurrency**: All coroutines must run in supervised scopes (`SupervisorJob()`).
- **Cancellation Safety**: In catch blocks, ALWAYS rethrow `CancellationException` and `JobCancellationException`:
  ```kotlin
  try {
      suspendAction()
  } catch (e: CancellationException) {
      throw e
  } catch (e: Exception) {
      logger.error("Failed doing work", e)
  }
  ```

### Storage & Test Isolation (CRITICAL)
- **Zero Test Contamination**: Unit and integration tests must NEVER write to or read from `~/.artwallpaper` (the user's real desktop storage).
- All tests must instantiate storage and managers with a temporary isolated directory:
  ```kotlin
  val tempDir = Files.createTempDirectory("isolated-test")
  val storage = ArtworkStorageManager(tempDir)
  ```

### Wallpaper Engine & Aesthetics
- **Background**: Ambient bokeh blur (`fastBoxBlur`) + 20% alpha wash + radial vignette. Do not modify or remove background styling without explicit design instructions.
- **Foreground Artwork**: Every artwork rendered to wallpaper must be framed with the authentic walnut hardwood moulding, gilded brass fillet inlay, archival sight-edge bevel, and ambient gallery drop shadow (`ImageProcessor.renderFramedArtwork`).
- **Operating System Sync**: Always ensure the app's in-memory artwork state matches the operating system's active desktop wallpaper (`wallpaperService.getCurrentWallpaper()`).

---

## 3. Available Project Skills

Custom project skills are available in `.agents/skills/`:
- **`art-contribute`**: Standard workflow for branching, local verification, and submitting PRs.
- **`art-museum-engine`**: Deep architectural manual for `ImageProcessor`, `WallpaperManager`, and display resolution scaling.
- **`art-release-ops`**: Maintainer guide for single-source version bumping and GitHub Actions release workflows.
