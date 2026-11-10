---
name: art-contribute
description: Comprehensive workflow guide and technical manual for contributors and AI agents developing, testing, and submitting pull requests to the Art Wallpaper project. Enforces master branch protection, sole owner review and merge governance, isolated testing standards, and architectural conventions.
---

# Art Wallpaper Contributor & Agent Development Manual

This document is the definitive operational manual for human developers and AI agents (Antigravity, Claude, Cursor, Copilot) contributing code, fixing bugs, or writing tests for **Art Wallpaper**.

---

## 1. Governance & Contribution Mandates

To safeguard application stability, user privacy, and release integrity, the following rules apply without exception:

1. **`master` is Strictly Protected**:
   - Direct commits and direct pushes to `master` are disabled.
   - Any attempt to push directly to `master` will be rejected by repository branch protection.
   - All work must be conducted on dedicated topic branches (`feat/*`, `fix/*`, `perf/*`, `docs/*`, `refactor/*`, `test/*`, `chore/*`).
2. **Owner-Only Merges & Releases**:
   - The repository owner ([@Princeyyyy](https://github.com/Princeyyyy)) holds exclusive authority to approve pull requests, merge into `master`, and trigger official release builds.
   - No external contributor, automated agent, or collaborator may merge pull requests.
3. **Mandatory CI Testing Gates**:
   - Every pull request triggers GitHub Actions automated tests on **Windows** (`windows-latest`), **macOS** (`macos-latest`), and **Web** (`ubuntu-latest`).
   - Pull requests must achieve a 100% green test pass rate before being considered for review.
4. **Zero Test Contamination (CRITICAL)**:
   - Unit and integration tests must **never** read from, write to, or delete files in the real user storage directory (`~/.artwallpaper` or `%USERPROFILE%\.artwallpaper`).
   - All tests must use isolated temporary directories instantiated with `Files.createTempDirectory(...)`.
5. **No Emojis Policy**:
   - All code, UI strings, dialogs, curatorial labels, commit messages, documentation, and release notes must be completely free of emojis. A clean, sophisticated, native desktop aesthetic must be maintained at all times.

---

## 2. End-to-End Git & PR Lifecycle

### Step 1: Fork and Clone

```bash
# 1. Clone your fork locally
git clone https://github.com/<your-username>/art-wallpaper.git
cd art-wallpaper

# 2. Add the upstream canonical repository
git remote add upstream https://github.com/Princeyyyy/art-wallpaper.git
git fetch upstream
```

### Step 2: Create a Dedicated Feature Branch

Always branch off the latest upstream `master`:

```bash
git checkout master
git fetch upstream
git rebase upstream/master
git checkout -b feat/your-feature-name
```

#### Branch Naming Guidelines
- `feat/<feature-name>`: New functionality, UI enhancements, or museum source integrations.
- `fix/<bug-description>`: Bug fixes, exception handling, or edge-case resolutions.
- `perf/<optimization>`: Performance improvements (e.g., bitmap caching, blur algorithms, memory footprint).
- `test/<coverage>`: New unit tests, concurrency tests, or resilience verifications.
- `docs/<subject>`: Documentation updates, architectural guides, or API specifications.
- `refactor/<subsystem>`: Structural refactoring without altering external behavior.

### Step 3: Implement Changes Following Architectural Standards

Adhere strictly to the coding standards detailed in Section 3 of this document.

### Step 4: Run Local Verification

Execute the verification matrix locally before staging commits:

```bash
# Compile Kotlin sources
./gradlew compileKotlin

# Run all unit and integration tests
./gradlew test

# If web landing page is modified:
cd web && npm test && npm run build && cd ..
```

### Step 5: Format Conventional Commits

Commit messages must adhere to the Conventional Commits specification:

```bash
git add <modified-files>
git commit -m "feat(image): add multi-monitor DPI scaling to picture frame engine"
```

Allowed prefixes:
- `feat:` User-facing or engine feature additions
- `fix:` Bug fixes and error handling corrections
- `perf:` Performance optimizations
- `test:` Unit, integration, or stress test additions
- `docs:` Documentation and handbook updates
- `refactor:` Code refactoring
- `chore:` Dependency bumps, CI configuration, or build tooling updates

### Step 6: Rebase onto Upstream Master

Before pushing, rebase your branch on the latest upstream commits:

```bash
git fetch upstream
git rebase upstream/master
```

If conflicts arise, resolve them locally, test thoroughly, and continue:

```bash
git rebase --continue
```

### Step 7: Push and Open a Pull Request

Push your feature branch to your personal fork:

```bash
git push -u origin feat/your-feature-name
```

Navigate to `https://github.com/Princeyyyy/art-wallpaper` and open a Pull Request targeting `master`. Complete the structured Pull Request Template in full:
- Provide a concise summary of the problem and your solution.
- Link relevant issues (e.g., `Closes #123`).
- Confirm local tests passed with 0 failures.
- Attach before/after screenshots if any UI elements were modified.

---

## 3. Architectural Guidelines & Conventions

### Language & Runtime
- **Kotlin**: 2.1.0 targeting JVM 17 (`jvmToolchain(17)`).
- **UI Framework**: Jetpack Compose Desktop 1.8.0-alpha03.
- **Serialization**: `kotlinx.serialization` (JSON).
- **HTTP Client**: OkHttp 4.12.0 with strict connection and read timeouts.
- **Logging**: SLF4J with Logback 1.5.12.

### Coroutines & Concurrency Rules
- **Structured Concurrency**: All long-lived coroutines must run within supervised scopes (`CoroutineScope(SupervisorJob() + Dispatchers.Default)`).
- **Thread Dispatching**:
  - File I/O, registry access, process spawning, and network operations must execute on `Dispatchers.IO`.
  - UI state rendering and Compose mutations must execute on `Dispatchers.Main`.
- **Cancellation Safety (MANDATORY)**:
  In any `try/catch` block within coroutines, `CancellationException` and `JobCancellationException` must **always** be rethrown immediately:
  ```kotlin
  try {
      performBackgroundTask()
  } catch (e: CancellationException) {
      throw e
  } catch (e: Exception) {
      logger.error("Operation failed", e)
  }
  ```
  Failing to rethrow `CancellationException` breaks coroutine cancellation hierarchies and leads to stale threads during app shutdown and test execution.

### Single Source of Truth for Versioning
- The version string is declared exclusively in `gradle.properties`:
  ```properties
  appVersion=4.1.5
  ```
- Source code dynamically reads `AppInfo.version` from generated properties. **Never** hardcode version strings in source files.

### Storage & Test Isolation Standards
- User data resides in `%USERPROFILE%\.artwallpaper` on Windows, and `~/.artwallpaper` on macOS.
- **Test Rule**: Tests must never access or modify the user's home directory.
  ```kotlin
  @Test
  fun `storage manager properly prunes old files`() {
      val tempDir = Files.createTempDirectory("art-test-isolated")
      try {
          val storage = ArtworkStorageManager(tempDir)
          // Test logic here
      } finally {
          tempDir.toFile().deleteRecursively()
      }
  }
  ```

### Museum Wallpaper & Picture Frame Engine
- Wallpapers are rendered at exact screen pixel dimensions by `ImageProcessor.processImage`.
- **Ambient Bokeh Background**: Downsamples artwork, executes multi-pass separable box blur (`fastBoxBlur`), applies a 20% alpha wash (`Color(0, 0, 0, 55)`), and applies a radial vignette. **Do not modify background styling.**
- **Physical Walnut Picture Frame**: Every artwork rendered onto the wallpaper is framed with walnut hardwood moulding, fluted cove relief, gilded brass fillet inlay, archival sight-edge bevel, and ambient gallery drop shadow (`ImageProcessor.renderFramedArtwork`).

---

## 4. Local Testing & Verification Matrix

| Task | Command | Purpose |
|---|---|---|
| **Compile Code** | `./gradlew compileKotlin` | Verifies syntax, types, and compiler warnings. |
| **Run Unit Tests** | `./gradlew test` | Runs all unit and integration tests. |
| **Single Test Class** | `./gradlew test --tests "ArtworkStorageManagerTest"` | Executes targeted test suites. |
| **Launch Desktop App** | `./gradlew run` | Runs the desktop Compose UI in local development mode. |
| **Package Windows MSI** | `.\gradlew packageMsi` | Packages local WiX MSI installer on Windows. |
| **Package macOS DMG** | `./gradlew packageDmg` | Packages local DMG disk image on macOS. |
| **Web Landing Page** | `cd web && npm run build` | Builds the Next.js marketing and showcase website. |

---

## 5. Troubleshooting Common Contributor Issues

### Gradle Daemon Socket Connection Failure on Windows
If Gradle reports `Could not connect to the Gradle daemon. Listening on ... [localhost/127.0.0.1]`:
- Windows sandbox network isolation blocks inter-process socket communication on localhost.
- **Solution**: Run Gradle with `--no-daemon` or execute the command outside the sandbox boundary:
  ```powershell
  .\gradlew test --no-daemon
  ```

### File Lock Contention on Windows
If tests or builds fail with `FileSystemException: The process cannot access the file because it is being used by another process`:
- Ensure all `FileInputStream`, `FileOutputStream`, and image buffers are wrapped in `.use { ... }` blocks.
- Explicitly invoke `bufferedImage.flush()` after graphics processing to release off-heap DirectX/GDI memory buffers immediately.

### Corrupted Settings Resilience
- The settings manager maintains both `settings.json` and `settings.json.bak`. If `settings.json` is corrupted, the application automatically recovers from `.bak`. Tests verifying this must assert that no crash occurs and default settings are restored if both files are invalid.
